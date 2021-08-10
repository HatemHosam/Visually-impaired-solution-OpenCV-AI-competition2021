#!/usr/bin/env python3
import numpy as np # numpy - manipulate the packet data returned by depthai
from bluedot.btcomm import BluetoothServer
from signal import pause
import cv2 # opencv - display the video stream
import depthai as dai# access the camera and its data packets
from ade20k_colormap import create_ade20k_label_colormap, classes
from scipy.ndimage import label
import time
#to organize data sending on bluetooth
last = time.time()

# bluetooth functions
def data_received(data):
    print(data)
    
def client_connect():
    print('client is conneced')
    s.send('you are now connected to the AI kit')

#create bluetooth server
s = BluetoothServer(data_received, when_client_connects = client_connect)

#color map for Ade20k segmentation output 
colormap = create_ade20k_label_colormap()  #colormap for ADE20K

stepSize = 0.05

newConfig = False
# Create pipeline
pipeline = dai.Pipeline()

# Define sources for Depth
monoLeft = pipeline.createMonoCamera()
monoRight = pipeline.createMonoCamera()
stereo = pipeline.createStereoDepth()
spatialLocationCalculator = pipeline.createSpatialLocationCalculator()
# Define sources for segmentation
cam_rgb = pipeline.createColorCamera()
cam_rgb.setPreviewSize(512,512)
cam_rgb.setInterleaved(False)

segmentation_nn = pipeline.createNeuralNetwork()
segmentation_nn.setBlobPath('Deeplabv3+512x512_FP32_13shaves.blob')

# Define outputs for Semantic segmentation
cam_rgb.preview.link(segmentation_nn.input)
xout_rgb = pipeline.createXLinkOut()
xout_rgb.setStreamName("rgb")
cam_rgb.preview.link(xout_rgb.input)

xout_nn = pipeline.createXLinkOut()
xout_nn.setStreamName("nn")
segmentation_nn.out.link(xout_nn.input)

frame = None
# Define outputs for Depth
xoutDepth = pipeline.createXLinkOut()
xoutSpatialData = pipeline.createXLinkOut()
xinSpatialCalcConfig = pipeline.createXLinkIn()

xoutDepth.setStreamName("depth")
xoutSpatialData.setStreamName("spatialData")
xinSpatialCalcConfig.setStreamName("spatialCalcConfig")

# Properties
monoLeft.setResolution(dai.MonoCameraProperties.SensorResolution.THE_400_P)
monoLeft.setBoardSocket(dai.CameraBoardSocket.LEFT)
monoRight.setResolution(dai.MonoCameraProperties.SensorResolution.THE_400_P)
monoRight.setBoardSocket(dai.CameraBoardSocket.RIGHT)

lrcheck = False
subpixel = False

stereo.setConfidenceThreshold(255)
stereo.setLeftRightCheck(lrcheck)
stereo.setSubpixel(subpixel)

# Config
topLeft = dai.Point2f(0.4, 0.4)
bottomRight = dai.Point2f(0.6, 0.6)

config = dai.SpatialLocationCalculatorConfigData()
config.depthThresholds.lowerThreshold = 100
config.depthThresholds.upperThreshold = 10000
config.roi = dai.Rect(topLeft, bottomRight)

spatialLocationCalculator.setWaitForConfigInput(False)
spatialLocationCalculator.initialConfig.addROI(config)

# Linking
monoLeft.out.link(stereo.left)
monoRight.out.link(stereo.right)

spatialLocationCalculator.passthroughDepth.link(xoutDepth.input)
stereo.depth.link(spatialLocationCalculator.inputDepth)

spatialLocationCalculator.out.link(xoutSpatialData.input)
xinSpatialCalcConfig.out.link(spatialLocationCalculator.inputConfig)

# Connect to device and start pipeline
with dai.Device(pipeline) as device:
    #for semantic segmentation
    #device.startPipeline()
    q_rgb = device.getOutputQueue("rgb",  maxSize=3, blocking=False)
    q_nn = device.getOutputQueue("nn", maxSize=3, blocking=False)
    frame = None
    # Output queue will be used to get the depth frames from the outputs defined above
    depthQueue = device.getOutputQueue(name="depth", maxSize=4, blocking=False)
    spatialCalcQueue = device.getOutputQueue(name="spatialData", maxSize=4, blocking=False)
    spatialCalcConfigInQueue = device.getInputQueue("spatialCalcConfig")

    color = (255, 255, 255)

    while True:
        xmin = 0
        xmax = 0
        ymin = 0
        ymax = 0
        dist = 0
        
        inDepth = depthQueue.get() # Blocking call, will wait until a new data has arrived

        depthFrame = inDepth.getFrame()
        depthFrameColor = cv2.normalize(depthFrame, None, 255, 0, cv2.NORM_INF, cv2.CV_8UC1)
        depthFrameColor = cv2.equalizeHist(depthFrameColor)
        depthFrameColor = cv2.applyColorMap(depthFrameColor, cv2.COLORMAP_HOT)

        spatialData = spatialCalcQueue.get().getSpatialLocations()
        for depthData in spatialData:
            roi = depthData.config.roi
            roi = roi.denormalize(width=depthFrameColor.shape[1], height=depthFrameColor.shape[0])
            #coordinates of the center box
            xmin = int(roi.topLeft().x)
            ymin = int(roi.topLeft().y)
            xmax = int(roi.bottomRight().x)
            ymax = int(roi.bottomRight().y)
            #z distance from camera using stereo depth
            dist = np.rint(depthData.spatialCoordinates.z/1000)
            depthMin = depthData.depthMin
            depthMax = depthData.depthMax

            fontType = cv2.FONT_HERSHEY_TRIPLEX
            cv2.rectangle(depthFrameColor, (xmin, ymin), (xmax, ymax), color, cv2.FONT_HERSHEY_SCRIPT_SIMPLEX)
            cv2.putText(depthFrameColor, f"X: {int(depthData.spatialCoordinates.x)/10} cm", (xmin + 10, ymin + 20), fontType, 0.5, 255)
            cv2.putText(depthFrameColor, f"Y: {int(depthData.spatialCoordinates.y)/10} cm", (xmin + 10, ymin + 35), fontType, 0.5, 255)
            cv2.putText(depthFrameColor, f"Z: {int(depthData.spatialCoordinates.z)/10} cm", (xmin + 10, ymin + 50), fontType, 0.5, 255)
        
        # Show the depth frame
        cv2.imshow("depth", depthFrameColor)
        
        #for semantic segmentation
        in_rgb = q_rgb.get()
        in_nn = q_nn.get()
        out = np.zeros((512,512,3), dtype= np.uint8)
        if in_rgb is not None:
            shape = (3, in_rgb.getHeight(), in_rgb.getWidth())
            frame = in_rgb.getData().reshape(shape).transpose(1, 2, 0).astype(np.uint8)
            frame = np.ascontiguousarray(frame)
        if in_nn is not None:
            layers = in_nn.getAllLayers()
            layer1 = in_nn.getLayerInt32(layers[0].name)
            #print(layer1)
            seg = np.rint(layer1).reshape(512,512)
            seg = seg - 1
            #labels = np.uint8(np.unique(seg)).tolist()
            print(np.unique(seg))
            for i in range(150):
                out[seg == i,:] = colormap[i]
            #cv2.imshow("seg", out)
            #cv2.imshow("preview", frame)
            added_image = cv2.addWeighted(frame,0.7,out,0.5,0)
            cv2.imshow("pSeg", added_image)
            #pavement localization
            if classes.index('sidewalk;pavement') in np.unique(seg).tolist():
                pave_mask = np.where(seg == 11)

                if not (xmin in pave_mask[1] and xmax in pave_mask[1] and ymin in pave_mask[0] and ymax in pave_mask[0]):
                    if xmin < np.amin(pave_mask[1]) and len(pave_mask[0]) > 2000:
                        text = 'the pavement is on your right'
                        now = time.time()
                        if now - last > 3:
                            s.send(text)
                            ast = now
                    elif xmax > np.amax(pave_mask[1])and len(pave_mask[0]) > 2000:
                        text = 'the pavement is on your left'
                        now = time.time()
                        if now - last > 3:
                            s.send(text)
                            last = now
                    else:
                        text = ''
                    print(text)
            #person detection
            if classes.index('person') in np.unique(seg).tolist():
                print(seg.shape)
                loc2 = np.where(seg == 12)
                dist = np.rint(dist)
                if xmin in loc2[1] and xmax in loc2[1] and ymin in loc2[0] and ymax in loc2[0] and len(loc2[0]) > 3000 and dist < 3:
                    text = 'watch out, a person is in front of you'
                    #check that 3 seconds has left from last sent command to send new command
                    now = time.time()
                    if now - last > 3:
                        s.send(text)
                        last = now
                else:
                    text = ''
                print(text)
            #vehicle detection
            if classes.index('car;auto;automobile') in np.unique(seg).tolist():
                print(seg.shape)
                loc3 = np.where(seg == 20)
                dist = np.rint(dist)
                if xmin in loc3[1] and xmax in loc3[1] and ymin in loc3[0] and ymax in loc3[0] and len(loc3[0]) > 3000 and dist < 3:
                    text = 'watch out, a vehicle is in front of you'
                     #check that 3 seconds has left from last sent command to send new command
                    now = time.time()
                    if now - last > 3:
                        s.send(text)
                        last = now
                else:
                    text = ''
                print(text)
            
            #Motorbike detection
            if classes.index('minibike;motorbike') in np.unique(seg).tolist():
                loc4 = np.where(seg == classes.index('minibike;motorbike'))
                dist = np.rint(dist)
                if xmin in loc4[1] and xmax in loc4[1] and ymin in loc4[0] and ymax in loc4[0] and len(loc4[0]) > 3000 and dist < 3:
                    text = 'watch out, a motorbike is in front of you'
                     #check that 3 seconds has left from last sent command to send new command
                    now = time.time()
                    if now - last > 3:
                        s.send(text)
                        last = now
                else:
                    text = ''
                print(text)
            #bicycle detection
            if classes.index('bicycle;bike;wheel;cycle') in np.unique(seg).tolist():
                loc5 = np.where(seg == classes.index('bicycle;bike;wheel;cycle'))
                dist = np.rint(dist)
                if xmin in loc5[1] and xmax in loc5[1] and ymin in loc5[0] and ymax in loc5[0] and len(loc5[0]) > 3000 and dist < 3:
                    text = 'watch out, a bicycle is in front of you'
                     #check that 3 seconds has left from last sent command to send new command
                    now = time.time()
                    if now - last > 3:
                        s.send(text)
                        last = now
                else:
                    text = ''
                print(text)
            #tree detection
            if classes.index('tree') in np.unique(seg).tolist():
                loc6 = np.where(seg == classes.index('tree'))
                if xmin in loc6[1] and xmax in loc6[1] and ymin in loc6[0] and ymax in loc6[0] and len(loc6[0]) > 5000 and dist < 3:
                    text = 'watch out, a tree is in front of you'
                     #check that 3 seconds has left from last sent command to send new command
                    now = time.time()
                    if now - last > 3:
                        s.send(text)
                        last = now
                else:
                    text = ''
                print(text)
        #close when press q
        key = cv2.waitKey(1)
        if key == ord('q'):
            break