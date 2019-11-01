This repository is for a 5G experimenta in XJTLU.

## 实验流程

#### 1. 先做4G部分的增加实验

- 增加静态时延：飞机在一定高度静止10分钟，得出10分钟内平均延迟
- 增加动态时延：飞机在航线上飞行时测量时延，绕圈圈即可。
- 须在不同高度尝试上述两个时延，并进行对比

**软件部分变动：需要增加时延测量的代码，client和server需要进行配合，并且结果要去掉软件运行时间**

#### 2. 再做5G部分的实验

- 大概按照之前paper的来做
- 重点也是延迟，其次是丢包率


### Client Folder

The folder contains the Android source code of the client software used in my experiment. You need to download an Android Studio to compile it to a cell phone.

PS: you can edit the IP address in the source code to start your own demo.

### Server Folder

The python script running in the server is contained in this folder. You can implement the script in a cloud server.

**Mongodb is required in the server**

### Data Folder

This folder contains collected data in our experiment.