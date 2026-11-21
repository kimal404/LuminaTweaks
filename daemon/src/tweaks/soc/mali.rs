use super::{mali_power_policy, SocProfile};

pub fn apply_power_policy(profile: SocProfile) -> usize {
    mali_power_policy(profile)
}
