# AIM4CodeBase2 
This project is based on AIM4, which is a 100% Java-based autonomous intersection management system with a built-in simulation environment. AIM4 was built by Stone et al. at University of Texas: https://www.cs.utexas.edu/~aim/. 
AIM4 is currently maintained by Tsz-Chiu Au <chiu@cs.utexas.edu>.
<br>
<br>

<b> Changes Made to the Original Code: </b>

   -Added additional metrics for Gross Throughput, Average Time Delay per vehicle, Max Time Delay and Standard Deviation of Time Delay
   
   -Also prints the number of vehicles coming from a lane and going to each lane at each time step (this will be used to dynamically route traffic)
   
   -Attempted to implement Dynamic Policy to reduce Congestion (Not yet working)
   
   -New method to reject reservation requests that are not coming in from a currently congested lane
<br>

   
<b>Supports:</b>
   
   -Multiple Intersections
      
   -Variation in vehicle speed
   
   <br>
   
<b>Does Not support: </b> 
   
   -platooning
   
   -Heterogenous traffic (self-driving and human)

   <br>

# To Run Local Simulation: 

1. Install Apache Maven (version >= 2.2.1):

2. Go to root directory

3. 
   To compile, type

   mvn -Dmaven.test.skip=true assembly:assembly


4. 
    To run, type

    java -jar target/AIM4-1.0-SNAPSHOT-jar-with-dependencies.jar

<br>
<br>
<br>
<b>Additional Notes:</b>
<br>
<br>

To execute a particular main function, type

  java -cp target/AIM4-1.0-SNAPSHOT-jar-with-dependencies.jar <YOUR_MAIN_FUNCTION>

To check the coding style, type

  mvn checkstyle:checkstyle
  view target/checkstyle-result.xml

To clean up, type

  mvn clean

