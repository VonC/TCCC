This directory contains the source code of TeamCity VCS plugin for ClearCase integration.

The source code is licensed under Apache 2.0 license ( http://www.apache.org/licenses/LICENSE-2.0 )

NOTE: This source code is compatibe with TeamCity 4.0 EAP builds.

clearcase-standalone.ipr     - IntelliJ IDEA project for building the plugin
build-standalone.xml         - Ant (1.6.5+) build script file.
build-standalone.properties  - Property file to store location of TeamCity distribution

To build the plugin download TeamCity .tar.gz distribution, unpack it and modify build-standalone.properties file to store the location.
Run "ant -f build-standalone.xml" to build and package the plugin into "dist\clearcase.jar".

More information on TeamCity: http://www.jetbrains.net/confluence/display/TW