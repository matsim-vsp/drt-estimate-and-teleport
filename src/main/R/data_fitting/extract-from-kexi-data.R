library(tidyverse)
library(lubridate)
library(geosphere)
library(fitdistrplus)


setwd("/Users/luchengqi/Documents/GitHub/drt-estimate-and-teleport/src/main/R/data_fitting")
all_completed_trips <- read_delim("data/real-world-data/VIA_Rides_202307_202310.csv", delim = ";")

processed_data <- all_completed_trips %>%
  dplyr::select(Request.ID, Request.Creation.Time, Requested.Pickup.Time, Origin.Lng, Origin.Lat, Destination.Lng, Destination.Lat, Actual.Pickup.Time, Actual.Dropoff.Time, Number.of.Passengers) %>%
  mutate(pre_booking_time = Requested.Pickup.Time - Request.Creation.Time)

## Ride duration statistics
rides_data <- processed_data %>%
  dplyr::select(Request.ID, Origin.Lng, Origin.Lat, Destination.Lng, Destination.Lat, Actual.Pickup.Time, Actual.Dropoff.Time) %>%
  mutate(ride_duration = Actual.Dropoff.Time - Actual.Pickup.Time) %>%
  filter(complete.cases(.)) %>%
  filter(ride_duration < 1800) %>% # Assume trip duration above this value to be artifact
  filter(ride_duration > 60) %>% # Assume trip duration below this value also to be artifact
  rowwise() %>%
  mutate(euclidean_distance = as.double(distGeo(c(Origin.Lng,Origin.Lat),c(Destination.Lng,Destination.Lat)))) %>%
  mutate(ride_duration = as.double(ride_duration))
  

# plot ride duration against euclidean distance
model <- lm(ride_duration ~ euclidean_distance, data = rides_data)
summary(model)
model$coefficients

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
