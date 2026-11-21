pub fn min_frequency(values: &[u64]) -> Option<u64> {
    values.iter().copied().min()
}

pub fn max_frequency(values: &[u64]) -> Option<u64> {
    values.iter().copied().max()
}

pub fn mid_frequency(values: &[u64]) -> Option<u64> {
    if values.is_empty() {
        return None;
    }
    let mut sorted = values.to_vec();
    sorted.sort_unstable_by(|a, b| b.cmp(a));
    let mid_index = (sorted.len() - 1) / 2;
    Some(sorted[mid_index])
}

pub fn nearest_frequency(values: &[u64], target: u64) -> Option<u64> {
    values.iter().copied().min_by_key(|value| value.abs_diff(target))
}

#[cfg(test)]
mod tests {
    use super::{max_frequency, mid_frequency, min_frequency, nearest_frequency};

    #[test]
    fn picks_median_frequency() {
        assert_eq!(mid_frequency(&[100, 200, 300, 400, 500]), Some(300));
        assert_eq!(mid_frequency(&[500, 100, 300, 200, 400]), Some(300));
        assert_eq!(mid_frequency(&[100, 200, 300, 400]), Some(300));
        assert_eq!(mid_frequency(&[]), None);
    }

    #[test]
    fn picks_nearest_step() {
        assert_eq!(nearest_frequency(&[100, 300, 500], 260), Some(300));
        assert_eq!(nearest_frequency(&[100, 300, 500], 499), Some(500));
        assert_eq!(nearest_frequency(&[100, 300, 500], 90), Some(100));
        assert_eq!(nearest_frequency(&[], 300), None);
    }

    #[test]
    fn picks_extremes() {
        assert_eq!(min_frequency(&[400, 100, 300]), Some(100));
        assert_eq!(max_frequency(&[400, 100, 300]), Some(400));
        assert_eq!(min_frequency(&[]), None);
        assert_eq!(max_frequency(&[]), None);
    }
}
