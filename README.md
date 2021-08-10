# Visually-impaired-solution-OpenCV-AI-competition2021

We propose an AI-based computer vision hardware/software implementation to solve the problem of
the individual street navigation for visually impaired/blind individuals. With the aid of the current
achievements of semantic segmentation and depth estimation, we could integrate those great computer
vision algorithms in our solution to provide a street-scene perception.
The recent semantic segmentation methods which are trained on the popular segmentation dataset
“ADE20K” provide a detailed indoor and outdoor scenes understanding via segmentation. This model can be easily integrated in an AI neural inference unit with camera like OAK-D or raspberry pi. Another
tool is integrated in our application which is the GPS which receive the desired location by the user, this GPS service is implemented using mobile application which receive the voice command get it on Google maps, then the smart phone provide the directions to the user through a custom mobile application, during the GPS navigation the OAK-D board is visualizing the street and provide the specific street pavement positioning, obstacle avoidance command until user reach to his destination.

hardware requirements: </br>
1- OpenCV AI Kit (OAK-D). </br>
2- Raspberry pi 4.</br>

Software requirements:</br>
1- python 3.7.0 or above.</br>
2- depth-ai='2.5.0.0'.</br>
3- opencv '4.5.1'.</br>
4- Numpy '1.19.5'.</br>

Mobile application build requirements:</br>
1-Android studio 4.2.2.</br>
