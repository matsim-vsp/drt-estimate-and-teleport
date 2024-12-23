library(tidyverse)
library(lubridate)
library(geosphere)
library(fitdistrplus)


#setwd("/Users/luchengqi/Documents/GitHub/drt-estimate-and-teleport/src/main/R/data_fitting")
all_completed_trips <- read_delim("/Users/luchengqi/Documents/TU-Berlin/Projects/KelRide/real-world-data/VIA_Rides_202307_202310-with-dir_tt.csv", delim = "\t")

processed_data <- all_completed_trips %>%
  dplyr::select(Request.ID, Request.Creation.Time, Requested.Pickup.Time, Origin.Lng, Origin.Lat, Destination.Lng, Destination.Lat, Actual.Pickup.Time, Actual.Dropoff.Time, Number.of.Passengers, direct_trip_duration) %>%
  mutate(pre_booking_time = Requested.Pickup.Time - Request.Creation.Time)

## Ride duration statistics
rides_data <- processed_data %>%
  dplyr::select(Request.ID, Origin.Lng, Origin.Lat, Destination.Lng, Destination.Lat, Actual.Pickup.Time, Actual.Dropoff.Time, direct_trip_duration) %>%
  mutate(ride_duration = Actual.Dropoff.Time - Actual.Pickup.Time) %>%
  filter(complete.cases(.)) %>%
  filter(ride_duration < 1800) %>% # Assume trip duration above this value to be artifact
  filter(ride_duration > 60) %>% # Assume trip duration below this value also to be artifact
  rowwise() %>%
  mutate(euclidean_distance = as.double(distGeo(c(Origin.Lng,Origin.Lat),c(Destination.Lng,Destination.Lat)))) %>%
  mutate(ride_duration = as.double(ride_duration)) %>%
  mutate(log_ride_duration = log(ride_duration))
  

# plot ride duration against euclidean distance
euclidean_dist_model <- lm(ride_duration ~ euclidean_distance, data = rides_data)
summary(euclidean_dist_model)
euclidean_dist_model$coefficients

# plot ride duration against direct trip duration
#dir_tt_model <- lm(log_ride_duration ~ direct_trip_duration, data = rides_data)
dir_tt_model <- lm(ride_duration ~ direct_trip_duration, data = rides_data)
summary(dir_tt_model)
dir_tt_model$coefficients

## Distribution of actual ride duration
ggplot(data = rides_data, aes(x = ride_duration)) + 
  geom_density(fill = "cyan", color = "blue", alpha = 0.5) +
  xlab("Ride duration [s]")


## Euclidean distance model
# Note: the slope and intercept are read from the output from the line above
ggplot(data = rides_data, aes(x = euclidean_distance, y = ride_duration)) +
  geom_point() +
  geom_abline(slope = 0.08762753, intercept = 316.04580609, color = "red", linetype="dashed", linewidth=1.5)

distribution_data <- rides_data %>%
  mutate(typical_ride_duration = 0.08762753 * euclidean_distance + 316.04580609) %>%
  mutate(normalized_ride_duration = ride_duration / typical_ride_duration)

# plot the distribution of ride duration around the linear regression
ggplot(distribution_data, aes(x = normalized_ride_duration)) +
  geom_density(fill = "orange", color = "red", alpha = 0.5) +
  labs(title = "Ride duration distribution", x = "Normalized ride duration", y = "Density") +
  xlim(0, 3)

## Direct trip duration model
# Note: the slope and intercept are read from the output from the line above
ggplot(data = rides_data, aes(x = direct_trip_duration, y = ride_duration)) +
  geom_point() +
  #geom_abline(slope = 0.002673351, intercept = 5.364422510, color = "magenta", linetype="dashed", linewidth=1.5) +
  geom_abline(slope = 1.219366, intercept = 177.523819, color = "magenta", linetype="dashed", linewidth=1.5) +
  xlab("Direct ride duration on MATSim network [s]") +
  ylab("Actual ride duration (based on VIA data) [s]") +
  theme_minimal() +
  labs(title = "Scatter plot of the actual ride data with linear regression") +
  theme(
    plot.title = element_text(hjust = 0.5, size = 20),  # Center the title
    axis.title.x = element_text(size = 18),  # Increase x-axis label font size
    axis.title.y = element_text(size = 18),  # Increase y-axis label font size
    axis.text.x = element_text(size = 16),   # Increase x-axis text font size
    axis.text.y = element_text(size = 16)    # Increase y-axis text font size
  ) 
  

distribution_data_dir_tt <- rides_data %>%
  mutate(typical_ride_duration = 1.219366 * direct_trip_duration + 177.523819) %>%
  mutate(normalized_ride_duration = ride_duration / typical_ride_duration)

ggplot(distribution_data_dir_tt) +
  geom_point(aes(x = direct_trip_duration, y = normalized_ride_duration))

# plot the distribution of ride duration around the linear regression
ggplot(distribution_data_dir_tt, aes(x = normalized_ride_duration)) +
  geom_density(fill = "pink", color = "magenta", alpha = 0.5) +
  labs(title = "Distribution of ride duration", x = "Normalized ride duration", y = "Density") +
  xlim(0, 3.5) +
  theme_minimal() +
  theme(
    plot.title = element_text(hjust = 0.5, size = 20),  # Center the title
    axis.title.x = element_text(size = 18),  # Increase x-axis label font size
    axis.title.y = element_text(size = 18),  # Increase y-axis label font size
    axis.text.x = element_text(size = 16),   # Increase x-axis text font size
    axis.text.y = element_text(size = 16)    # Increase y-axis text font size
  ) 

# Fit the log-normal distribution
#fit <- fitdist(distribution_data_dir_tt$normalized_ride_duration, "lnorm", method = "mle")
#fit <- fitdist(distribution_data_dir_tt$normalized_ride_duration, "lnorm", method = "mge")
fit <- fitdist(distribution_data_dir_tt$normalized_ride_duration, "lnorm", method = "mme")
#fit <- fitdist(distribution_data_dir_tt$normalized_ride_duration, "lnorm", method = "mde")
#fit <- fitdist(distribution_data_dir_tt$normalized_ride_duration, "lnorm", method = "qme")

summary(fit)
params <- fit$estimate
mu <- params['meanlog']
sigma <- params['sdlog']

plot(fit)

# Generate a data frame to plot the fitted log-normal distribution
x_values <- seq(0, 10, length.out = 10000)
density_values <- dlnorm(x_values, meanlog = mu, sdlog = sigma)
#density_values <- dlnorm(x_values, meanlog = -0.17, sdlog = 0.31) # Manual fitted value
plot_data <- data.frame(x = x_values, density = density_values)


breaks <- seq(0, max(distribution_data_dir_tt$normalized_ride_duration), by = 0.05)

# plot the fitted regression on the histogram
ggplot(distribution_data_dir_tt) +
  geom_histogram(aes(x = normalized_ride_duration, y = after_stat(density)), breaks = breaks, fill = "blue", alpha = 1) +
  xlim(0,3.5) +
  geom_line(data = plot_data, aes(x = x, y = density), color = "red", size = 1) +
  labs(title = "Distribution of normalized ride duration",
       x = "Value", y = "Density") +
  theme_minimal() +
  xlab("Normalized ride duration") +
  theme(
    plot.title = element_text(hjust = 0.5, size = 20),  # Center the title
    axis.title.x = element_text(size = 18),  # Increase x-axis label font size
    axis.title.y = element_text(size = 18),  # Increase y-axis label font size
    axis.text.x = element_text(size = 16),   # Increase x-axis text font size
    axis.text.y = element_text(size = 16)    # Increase y-axis text font size
  ) 

# Plot the log-normal distribution using ggplot2
# ggplot(plot_data, aes(x = x, y = density)) +
#   geom_line(color = "blue", size = 1) +
#   labs(title = "Log-Normal Distribution",
#        x = "x", y = "Density") +
#   xlim(0, 3.5)

## Waiting time statistics for spontaneous trips
# If the requested departure time is close to request creation time --> spontaneous trips
spontaneous_trips <- processed_data %>%
  filter(pre_booking_time < 600) %>%
  #mutate(waiting_time = Actual.Pickup.Time - Request.Creation.Time) %>%
  mutate(waiting_time = Actual.Pickup.Time - Requested.Pickup.Time) %>%
  mutate(waiting_time = ifelse(waiting_time < 0, 0, waiting_time))

# plot the distribution of waiting time
ggplot(spontaneous_trips, aes(x = waiting_time / 60)) +
  geom_density(fill = "cyan", color = "blue", alpha = 0.5) +
  labs(title = "Waiting time distribution", x = "Waiting time [minute]", y = "Density") +
  xlim(0, 45)
