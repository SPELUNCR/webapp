# webapp
This repository is for a web application that runs within an Apache Tomcat 9.0.39 server container. The web application uses Java servlet technology to run SPELUNCR's Geiger counter and IMU sensors and present the live data from these sensors on a web page. 

For documentation about Apache Tomcat 9 visit the [Apache Tomcat 9 Documentation](http://tomcat.apache.org/tomcat-9.0-doc/index.html)

Other versions of Apache Tomcat are available, but **there are no guarantees of compatability with the code in this repository and any Tomcat version except version 9.0.39.** [Download Apache Tomcat 9.0.39 Here](https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.39/bin/)

## Hardware
* [Raspberry Pi 4 model B](https://www.raspberrypi.org/products/raspberry-pi-4-model-b/specifications/)
* MPU6050 breakout board : 
	[Specification](https://invensense.tdk.com/wp-content/uploads/2015/02/MPU-6000-Datasheet1.pdf),
	[Register Map](https://invensense.tdk.com/wp-content/uploads/2015/02/MPU-6000-Register-Map1.pdf)
* [Geiger Counter](https://rhelectronics.net/store/radiation-detector-geiger-counter-diy-kit-second-edition.html)

## Dependencies
1. **pi4j-core-1.4** (Pi for Java version 1.4) : [The Pi4J Project](https://pi4j.com/1.4/index.html), [JavaDoc](https://pi4j.com/1.4/apidocs/index.html)
	* javax.activation-api-1.2.0
	* jaxb-api-2.3.1
1. **tomcat-websocket-api-9.0.39**:
	[Specification](https://jcp.org/aboutJava/communityprocess/mrel/jsr356/index.html),
	[JavaDoc](https://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html)
	**Note: Apache Tomcat 9.0.39 contains this dependency in the *lib* folder**
1. **tomcat-servlet-api-9.0.39**:
	[Specification](https://jcp.org/aboutJava/communityprocess/final/jsr369/index.html),
	[JavaDoc](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/package-summary.html)
	**Note: Apache Tomcat 9.0.39 contains this dependency in the *lib* folder**

## Building
The webapp can be built using the Apache Ant build tool and the build file *build.xml*. The build targets are: *all, build, clean, compile, prepare*.
* *all*		: The *all* target is used to perform both the *clean* and *compile* targets.
* *build*	: The *build* target depends on the *compile* target and creates a Web Application Archive (.war) in the *dist* directory. The *.war* file should be placed in the *webapps* directory of an Apache Tomcat server.
* *clean*	: The *clean* target deletes the *build* directory, which contains compiled java classes. *clean* also deletes the *dist* directory, which contains the web application archive.
* *compile*	: The *compile* target compiles all the Java source files in the *src* directory and places the resulting class files in the *build/WEB-INF/classes* directory. The *compile* target depends on the *prepare* target.
* *prepare*	: The *prepare* target creates the *build* directory structure that will contain the compiled Java classes (placed in *build/WEB-INF/classes*) and the static resources. The *prepare* target also places external libraries in the *build/WEB-INF/lib* directory.
