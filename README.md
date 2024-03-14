# drt-estimate-and-teleport
 
**Background and Motivation:** The service quality of DRT is highly sensitive to the number of users. In the conventional MATSim approach, we usually force a certain number of agents to try out new modes. For the scenarios with limited DRT supply (i.e., limited fleet size), overloading may happen. When overloading happens, the service quality of DRT system suffers a significant drop. This leads to agents having false memory about DRT service. One way out is to avoid this is to reduce the innovation rate until overloading does not happen. But doing so will increase the number of iterations needed for the model to converge, and that can be a major problem for larger scenarios.

**Solution: Estimate-and-Teleport** In PR3160, we implement a new way to perform simulation with DRT, namely estimate and teleport. Here we first come up with a DRT estimation model, based on real-world data (when available) and/or quantitative simulations (i.e., DRT simulations with fixed demands). In the MATSim iterative process, the DRT estimation model will generate individual estimated trip information for each agent (e.g., waiting time, ride duration, ride distance, probability of getting rejected). Then the agents will be teleported to the destination according to the estimated trip information. By doing so, we do not need to worry about the overloading problem, even with relatively high innovation rate.

To make this model work, we need to make sure the estimator is accurate. First, we will need to have an idea of what level of service quality the DRT system can achieve. When adequate real-world data is available, then we can develop a distribution model based on the real-world data and used it for the simulation. Otherwise, we will need to first define the targeted service quality. After the MATSim simulation, we can perform post-simulation, where DRT is explicitly simulated based on the output DRT trips (which are fixed) from the MATSim simulation. With that, all the KPIs of DRT system can be acquired. Furthermore, the post-simulation can also be used to validate the DRT estimator. If the estimator is too far-off from the performance in the post-simulation, then we may adjust the DRT estimator or DRT operational strategy.

In this repository we experiment with the newly implmented DRT estimate-and-teleport in different scenarios and observe its performance. 