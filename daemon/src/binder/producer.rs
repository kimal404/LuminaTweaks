/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Shared state and bounded event queue for the binder helper.
//!
//! Binder callbacks only update state and enqueue events; socket I/O happens
//! on the writer thread in `binder_ipc.rs`. Focus and screen events share a
//! bounded latest-wins queue (max 8). Process-death events are stored in a
//! separate deduplicated queue so they can never be coalesced or dropped.

use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Condvar, Mutex, MutexGuard};
use std::time::{Duration, Instant};

pub const PROTO_VERSION: u32 = 1;
pub const QUEUE_CAPACITY: usize = 8;

const LEGACY_FOCUS_TTL: Duration = Duration::from_secs(2);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Focus {
    pub package: String,
    pub pid: i32,
    pub uid: i32,
}

#[derive(Clone, Debug)]
pub struct Death {
    pub pid: i32,
    pub uid: i32,
    pub package: Option<String>,
}

#[derive(Clone, Debug)]
pub enum Event {
    Focus(Focus),
    Died(Death),
    Screen(bool),
}

struct State {
    screen_on: bool,
    focus: Option<Focus>,
    focus_at: Option<Instant>,
}

pub struct Shared {
    state: Mutex<State>,
    queue: Mutex<VecDeque<Event>>,
    queue_cv: Condvar,
    deaths: Mutex<VecDeque<Death>>,
    lifecycle: Mutex<bool>,
    legacy: Mutex<()>,
    legacy_cv: Condvar,
    legacy_events: AtomicU64,
    screen_request: Mutex<bool>,
    screen_cv: Condvar,
    legacy_consumed: AtomicU64,
    seq: AtomicU64,
    screen_ready: AtomicBool,
}

impl Default for Shared {
    fn default() -> Self {
        Self::new()
    }
}

impl Shared {
    pub fn new() -> Self {
        Self {
            state: Mutex::new(State {
                screen_on: true,
                focus: None,
                focus_at: None,
            }),
            queue: Mutex::new(VecDeque::with_capacity(QUEUE_CAPACITY)),
            queue_cv: Condvar::new(),
            deaths: Mutex::new(VecDeque::new()),
            lifecycle: Mutex::new(false),
            legacy: Mutex::new(()),
            legacy_cv: Condvar::new(),
            legacy_events: AtomicU64::new(0),
            screen_request: Mutex::new(false),
            screen_cv: Condvar::new(),
            legacy_consumed: AtomicU64::new(0),
            seq: AtomicU64::new(0),
            screen_ready: AtomicBool::new(true),
        }
    }

    fn lock_state(&self) -> MutexGuard<'_, State> {
        self.state
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn lock_queue(&self) -> MutexGuard<'_, VecDeque<Event>> {
        self.queue
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn lock_deaths(&self) -> MutexGuard<'_, VecDeque<Death>> {
        self.deaths
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn notify_legacy(&self) {
        self.legacy_events.fetch_add(1, Ordering::Relaxed);
        self.legacy_cv.notify_all();
    }

    pub fn snapshot(&self) -> (bool, Option<Focus>) {
        let state = self.lock_state();
        (state.screen_on, state.focus.clone())
    }

    pub fn set_initial_screen(&self, on: bool) {
        self.lock_state().screen_on = on;
        self.screen_ready.store(true, Ordering::Relaxed);
    }

    pub fn screen_ready(&self) -> bool {
        self.screen_ready.load(Ordering::Relaxed)
    }

    pub fn wait_screen_ready(&self, timeout: Duration) {
        let deadline = Instant::now() + timeout;
        while !self.screen_ready() && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(20));
        }
    }

    pub fn update_screen(&self, on: bool) -> bool {
        self.screen_ready.store(true, Ordering::Relaxed);
        let mut state = self.lock_state();
        if state.screen_on == on {
            return false;
        }
        state.screen_on = on;
        true
    }

    pub fn set_focus(&self, focus: Focus) -> bool {
        let mut state = self.lock_state();
        if state.focus.as_ref() == Some(&focus) {
            return false;
        }
        let pid = focus.pid;
        state.focus = Some(focus);
        state.focus_at = Some(Instant::now());
        drop(state);
        self.lock_deaths().retain(|death| death.pid != pid);
        self.notify_legacy();
        true
    }

    pub fn clear_focus(&self, pid: i32) -> Option<Focus> {
        let mut state = self.lock_state();
        let matches = state
            .focus
            .as_ref()
            .map(|focus| focus.pid == pid)
            .unwrap_or(false);
        if !matches {
            return None;
        }
        state.focus_at = None;
        let focus = state.focus.take();
        drop(state);
        self.notify_legacy();
        focus
    }

    pub fn take_focus(&self) -> Option<Focus> {
        let mut state = self.lock_state();
        let focus = state.focus.take();
        state.focus_at = None;
        focus
    }

    pub fn clear_focus_state(&self) {
        let mut state = self.lock_state();
        if state.focus.is_none() {
            return;
        }
        state.focus = None;
        state.focus_at = None;
        drop(state);
        self.notify_legacy();
    }

    pub fn legacy_focus(&self) -> Option<Focus> {
        let state = self.lock_state();
        let fresh = matches!(state.focus_at, Some(at) if at.elapsed() < LEGACY_FOCUS_TTL);
        if !fresh {
            return None;
        }
        state.focus.clone()
    }

    pub fn wait_legacy(&self, timeout: Duration) {
        let current = self.legacy_events.load(Ordering::Relaxed);
        if self.legacy_consumed.load(Ordering::Relaxed) != current {
            self.legacy_consumed.store(current, Ordering::Relaxed);
            return;
        }

        let guard = self
            .legacy
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let (guard, _) = self
            .legacy_cv
            .wait_timeout_while(guard, timeout, |_| {
                self.legacy_events.load(Ordering::Relaxed) == current
            })
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        drop(guard);
        self.legacy_consumed.store(
            self.legacy_events.load(Ordering::Relaxed),
            Ordering::Relaxed,
        );
    }

    pub fn next_seq(&self) -> u64 {
        self.seq.fetch_add(1, Ordering::Relaxed) + 1
    }

    /// Prepares shared state for a new producer session. Focus and screen
    /// events are stale once the client reconnects; pending deaths are kept so
    /// they are never lost across a daemon restart.
    pub fn begin_session(&self) {
        self.seq.store(0, Ordering::Relaxed);
        self.lock_queue().clear();
        self.notify_legacy();
    }

    pub fn reset_transient(&self) {
        self.begin_session();
        self.lock_deaths().clear();
    }

    pub fn push_event(&self, event: Event) {
        match event {
            Event::Died(death) => {
                let mut deaths = self.lock_deaths();
                if deaths.iter().any(|known| known.pid == death.pid) {
                    return;
                }
                if deaths.len() >= 256 {
                    deaths.pop_front();
                }
                deaths.push_back(death);
            }
            Event::Focus(focus) => {
                let pid = focus.pid;
                self.lock_deaths().retain(|death| death.pid != pid);
                let mut queue = self.lock_queue();
                if let Some(slot) = queue
                    .iter_mut()
                    .find(|item| matches!(item, Event::Focus(_)))
                {
                    *slot = Event::Focus(focus);
                } else if queue.len() < QUEUE_CAPACITY {
                    queue.push_back(Event::Focus(focus));
                } else if let Some(index) = queue
                    .iter()
                    .position(|item| matches!(item, Event::Focus(_)))
                {
                    queue.remove(index);
                    queue.push_back(Event::Focus(focus));
                }
            }
            Event::Screen(on) => {
                let mut queue = self.lock_queue();
                if let Some(slot) = queue
                    .iter_mut()
                    .find(|item| matches!(item, Event::Screen(_)))
                {
                    *slot = Event::Screen(on);
                } else if queue.len() < QUEUE_CAPACITY {
                    queue.push_back(Event::Screen(on));
                } else if let Some(index) = queue
                    .iter()
                    .position(|item| matches!(item, Event::Focus(_)))
                {
                    queue.remove(index);
                    queue.push_back(Event::Screen(on));
                }
            }
        }
        self.queue_cv.notify_one();
    }

    pub fn pop_event(&self, timeout: Duration) -> Option<Event> {
        let deadline = Instant::now() + timeout;
        let mut queue = self.lock_queue();
        loop {
            if let Some(death) = self.lock_deaths().pop_front() {
                return Some(Event::Died(death));
            }
            if let Some(event) = queue.pop_front() {
                return Some(event);
            }

            let now = Instant::now();
            if now >= deadline {
                return None;
            }

            let (guard, _) = self
                .queue_cv
                .wait_timeout(queue, deadline - now)
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            queue = guard;
        }
    }

    pub fn request_screen(&self) {
        if let Ok(mut guard) = self.screen_request.lock() {
            *guard = true;
        }
        self.screen_cv.notify_all();
    }

    pub fn wait_screen_request(&self, timeout: Duration) {
        let mut guard = self
            .screen_request
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if !*guard {
            let (next, _) = self
                .screen_cv
                .wait_timeout(guard, timeout)
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            guard = next;
        }
        *guard = false;
    }

    pub fn mark_dead(&self) {
        if let Ok(mut guard) = self.lifecycle.lock() {
            *guard = true;
        }
        self.queue_cv.notify_all();
        self.screen_cv.notify_all();
    }

    pub fn is_dead(&self) -> bool {
        match self.lifecycle.lock() {
            Ok(guard) => *guard,
            Err(poisoned) => *poisoned.into_inner(),
        }
    }

    pub fn clear_dead(&self) {
        if let Ok(mut guard) = self.lifecycle.lock() {
            *guard = false;
        }
    }

    pub fn wake(&self) {
        self.queue_cv.notify_all();
        self.screen_cv.notify_all();
        self.notify_legacy();
    }
}

pub fn monotonic_ms() -> u64 {
    let mut ts = libc::timespec {
        tv_sec: 0,
        tv_nsec: 0,
    };
    let result = unsafe { libc::clock_gettime(libc::CLOCK_MONOTONIC, &mut ts) };
    if result != 0 {
        return 0;
    }
    ts.tv_sec as u64 * 1000 + ts.tv_nsec as u64 / 1_000_000
}

#[cfg(test)]
mod tests {
    use super::*;

    fn focus(package: &str, pid: i32) -> Focus {
        Focus {
            package: package.to_string(),
            pid,
            uid: 10000 + pid,
        }
    }

    #[test]
    fn focus_is_latest_wins() {
        let shared = Shared::new();
        for index in 0..20 {
            shared.push_event(Event::Focus(focus(
                &format!("app{}.test", index),
                index + 1,
            )));
        }

        match shared.pop_event(Duration::ZERO) {
            Some(Event::Focus(value)) => assert_eq!(value.package, "app19.test"),
            other => panic!("unexpected event: {:?}", other),
        }
        assert!(shared.pop_event(Duration::ZERO).is_none());
    }

    #[test]
    fn death_survives_full_focus_queue() {
        let shared = Shared::new();
        for index in 0..QUEUE_CAPACITY {
            shared.push_event(Event::Focus(focus(
                &format!("app{}.test", index),
                index as i32 + 1,
            )));
        }
        shared.push_event(Event::Died(Death {
            pid: 77,
            uid: 10077,
            package: Some("game.test".to_string()),
        }));

        let mut found_death = false;
        while let Some(event) = shared.pop_event(Duration::ZERO) {
            if let Event::Died(death) = event {
                assert_eq!(death.pid, 77);
                assert_eq!(death.package.as_deref(), Some("game.test"));
                found_death = true;
            }
        }
        assert!(found_death);
    }

    #[test]
    fn death_is_deduplicated_and_clears_focus() {
        let shared = Shared::new();
        shared.set_focus(focus("game.test", 42));
        shared.push_event(Event::Died(Death {
            pid: 42,
            uid: 10042,
            package: Some("game.test".to_string()),
        }));
        shared.push_event(Event::Died(Death {
            pid: 42,
            uid: 10042,
            package: Some("game.test".to_string()),
        }));

        let first = shared.pop_event(Duration::ZERO);
        assert!(matches!(first, Some(Event::Died(_))));
        assert!(shared.pop_event(Duration::ZERO).is_none());
        assert_eq!(shared.take_focus(), Some(focus("game.test", 42)));
    }

    #[test]
    fn new_focus_drops_stale_death() {
        let shared = Shared::new();
        shared.push_event(Event::Died(Death {
            pid: 9,
            uid: 10009,
            package: None,
        }));
        shared.push_event(Event::Focus(focus("relaunch.test", 9)));

        match shared.pop_event(Duration::ZERO) {
            Some(Event::Focus(value)) => assert_eq!(value.pid, 9),
            other => panic!("unexpected event: {:?}", other),
        }
        assert!(shared.pop_event(Duration::ZERO).is_none());
    }

    #[test]
    fn screen_is_coalesced() {
        let shared = Shared::new();
        shared.push_event(Event::Screen(true));
        shared.push_event(Event::Screen(false));
        shared.push_event(Event::Screen(true));

        match shared.pop_event(Duration::ZERO) {
            Some(Event::Screen(value)) => assert!(value),
            other => panic!("unexpected event: {:?}", other),
        }
        assert!(shared.pop_event(Duration::ZERO).is_none());
    }

    #[test]
    fn sequence_increments_monotonically() {
        let shared = Shared::new();
        assert_eq!(shared.next_seq(), 1);
        assert_eq!(shared.next_seq(), 2);
        shared.reset_transient();
        assert_eq!(shared.next_seq(), 1);
    }

    #[test]
    fn death_never_dropped_by_screen_flood() {
        let shared = Shared::new();
        shared.push_event(Event::Died(Death {
            pid: 55,
            uid: 10055,
            package: Some("died.test".to_string()),
        }));
        for index in 0..(QUEUE_CAPACITY * 4) {
            shared.push_event(Event::Screen(index % 2 == 0));
            shared.push_event(Event::Focus(focus(
                &format!("app{}.test", index),
                index as i32 + 1,
            )));
        }

        let mut deaths = 0;
        while let Some(event) = shared.pop_event(Duration::ZERO) {
            if matches!(event, Event::Died(_)) {
                deaths += 1;
            }
        }
        assert_eq!(deaths, 1);
    }

    #[test]
    fn session_begin_keeps_pending_death() {
        let shared = Shared::new();
        shared.push_event(Event::Died(Death {
            pid: 1,
            uid: 10001,
            package: None,
        }));
        shared.push_event(Event::Focus(focus("a.test", 2)));
        shared.push_event(Event::Screen(false));
        shared.begin_session();

        match shared.pop_event(Duration::ZERO) {
            Some(Event::Died(death)) => assert_eq!(death.pid, 1),
            other => panic!("unexpected event: {:?}", other),
        }
        assert!(shared.pop_event(Duration::ZERO).is_none());
        assert_eq!(shared.next_seq(), 1);
    }

    #[test]
    fn reset_clears_everything() {
        let shared = Shared::new();
        shared.push_event(Event::Died(Death {
            pid: 1,
            uid: 10001,
            package: None,
        }));
        shared.push_event(Event::Focus(focus("a.test", 2)));
        shared.reset_transient();
        assert!(shared.pop_event(Duration::ZERO).is_none());
    }

    #[test]
    fn snapshot_returns_focus_and_screen() {
        let shared = Shared::new();
        shared.set_initial_screen(false);
        shared.set_focus(focus("game.test", 5));
        let (screen, focus) = shared.snapshot();
        assert!(!screen);
        assert_eq!(
            focus.map(|value| value.package),
            Some("game.test".to_string())
        );
    }
}
