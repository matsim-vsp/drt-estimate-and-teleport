library(tidyverse)

setwd("/Users/luchengqi/Documents/GitHub/drt-estimate-and-teleport/src/main/R/data_fitting")

# Kelheim scenario, without rejection, 95% satisfaction rate
alpha_15_beta_300_wt_300 <- read_delim("data/kelheim-no-rejection/alpha1.5-beta300-wt300.tsv", delim = "\t")
alpha_15_beta_450_wt_300 <- read_delim("data/kelheim-no-rejection/alpha1.5-beta450-wt300.tsv", delim = "\t")
alpha_15_beta_600_wt_300 <- read_delim("data/kelheim-no-rejection/alpha1.5-beta600-wt300.tsv", delim = "\t")
alpha_15_beta_750_wt_300 <- read_delim("data/kelheim-no-rejection/alpha1.5-beta750-wt300.tsv", delim = "\t")
alpha_15_beta_900_wt_300 <- read_delim("data/kelheim-no-rejection/alpha1.5-beta900-wt300.tsv", delim = "\t")


alpha_15_beta_600_wt_300 <- alpha_15_beta_600_wt_300 %>%
  mutate(max_ride_duration = 1.5 * direct_trip_duration + 600) %>%
  mutate(normalized_wait_time = total_wait_time / 300) #%>%
  select(submission, total_wait_time, normalized_wait_time, actual_ride_duration, direct_trip_duration, max_ride_duration)

# summary_data <- bind_rows(summary_data, alpha_15_beta_300_wt_300 %>%
#                             mutate(max_ride_duration = 1.5 * direct_trip_duration + 300) %>%
#                             mutate(normalized_wait_time = total_wait_time / 300) %>%
#                             select(submission, normalized_wait_time, direct_trip_duration, max_ride_duration)
#                           )

# normalized ride duration: 
alpha_15_beta_600_wt_300 <- alpha_15_beta_600_wt_300 %>% 
  mutate(normalized_ride_duration = (actual_ride_duration - direct_trip_duration) / (max_ride_duration - direct_trip_duration))

## Distribution on the range between direct ride time and max ride time
ggplot(alpha_15_beta_600_wt_300, aes(x = normalized_ride_duration)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Density Plot", x = "feasible range", y = "Density") +
  xlim(0,1.5)


ggplot(alpha_15_beta_600_wt_300 %>% filter(direct_trip_duration > 0 & direct_trip_duration < 1000), aes(x = normalized_ride_duration)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Density Plot", x = "feasible range", y = "Density") +
  xlim(0,1.5)


ggplot(alpha_15_beta_600_wt_300, aes(x = actual_ride_duration / max_ride_duration)) +
  geom_density(fill = "purple", color = "magenta", alpha = 0.5) +
  labs(title = "Density Plot", x = "actual ride duration / max ride duration", y = "Density") +
  xlim(0,1.5)

ggplot(alpha_15_beta_600_wt_300, aes(x = actual_ride_duration / direct_trip_duration)) +
  geom_density(fill = "cyan", color = "blue", alpha = 0.5) +
  labs(title = "Density Plot", x = "actual ride duration / direct trip duration", y = "Density") +
  xlim(0.75, 5.0)

#######



# Single case
simulated_model <- lm(actual_ride_duration ~ direct_trip_duration, data = alpha_15_beta_600_wt_300)
summary(simulated_model)
alpha_15_beta_600_wt_300 <- alpha_15_beta_600_wt_300 %>%
  mutate(reg_norm_ride_duration = actual_ride_duration / (2.28338 * direct_trip_duration + 57.14167)) %>%
  mutate(norm_ride_duration_direct = actual_ride_duration / direct_trip_duration) %>%
  mutate(normalized_ride_duration = actual_ride_duration / (1.5 * direct_trip_duration + 600)) %>%
  mutate(normalied_between_min_and_max = (actual_ride_duration - direct_trip_duration) /  ((1.5 * direct_trip_duration + 600) - direct_trip_duration))

ggplot(alpha_15_beta_600_wt_300, aes(x = direct_trip_duration, y = actual_ride_duration)) +
  geom_point() +
  geom_abline(slope = 2.28338, intercept = 57.14167, color="red", linetype = "dashed", size = 1.5)

## Distribution around regression line
ggplot(alpha_15_beta_600_wt_300, aes(x = reg_norm_ride_duration)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Density Plot", x = "t_act/t_reg", y = "Density") +
  xlim(0,3)

ggplot(alpha_15_beta_600_wt_300, aes(x = direct_trip_duration, y = reg_norm_ride_duration)) +
  geom_point()

## Distribution based on direct ride time
ggplot(alpha_15_beta_600_wt_300, aes(x = norm_ride_duration_direct)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Density Plot", x = "t_act/t_dir", y = "Density") +
  xlim(0,7)

ggplot(alpha_15_beta_600_wt_300, aes(x = direct_trip_duration, y = norm_ride_duration_direct)) +
  geom_point()

## Distribution based on max ride duration
ggplot(alpha_15_beta_600_wt_300, aes(x = normalized_ride_duration)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Density Plot", x = "t_act/t_max", y = "Density") +
  xlim(0,1.5)

ggplot(alpha_15_beta_600_wt_300, aes(x = direct_trip_duration, y = normalized_ride_duration)) +
  geom_point()




model_2 <- lm(normalied_between_min_and_max ~ direct_trip_duration, data = alpha_15_beta_600_wt_300)
summary(model_2)
model_2$coefficients

ggplot(alpha_15_beta_600_wt_300, aes(x = direct_trip_duration, y = normalied_between_min_and_max)) +
  geom_point() +
  geom_abline(slope = 0.001318525, intercept = 0.181439159, color="red", linetype = "dashed", size = 1.5)

alpha_15_beta_600_wt_300 <- alpha_15_beta_600_wt_300 %>%
  mutate(gamma = normalied_between_min_and_max / (0.001318525 * direct_trip_duration + 0.181439159))

ggplot(alpha_15_beta_600_wt_300 %>% filter(direct_trip_duration > 100 & direct_trip_duration < 300), aes(x = gamma)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Density Plot", x = "feasible range", y = "Density") +
  xlim(0,3.0)


## Combine direct (min) and max ride duration (max)

# Aggregated case (various alpha beta combinations)
ggplot(summary_data, aes(x = normalized_wait_time)) +
  geom_density(fill = "skyblue", color = "blue", alpha = 0.5) +
  labs(title = "Waiting time distribution", x = "Normalized wait time", y = "Density")

ggplot(summary_data, aes(x = normalized_ride_duration)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Ride duration", x = "normalized ride duration against max ride time", y = "Density") +
  theme(
    text = element_text(size = 14),  # for all text elements
    axis.title = element_text(size = 14),  # for axis titles
    axis.text = element_text(size = 14)  # for axis labels
  )

summary_data <- summary_data %>%
  arrange(normalized_wait_time) %>%
  mutate(wait_time_cdf = cumsum(normalized_wait_time) / sum(normalized_wait_time))

ggplot(summary_data, aes(x = normalized_wait_time, y = wait_time_cdf)) +
  geom_step() +
  labs(title = "ECDF Plot", x = "Wait time", y = "Cumulative Probability")


