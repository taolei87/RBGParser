#!/bin/sh

export LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:."

JNI_PATH="jni_include"

javac -d bin -sourcepath src -classpath "lib/trove.jar" src/parser/DependencyParser.java


javah -classpath bin utils.SVD
g++ -fPIC -shared -I./lib/SVDLIBC/ -I${JNI_PATH} -I${JNI_PATH}/linux libSVD.cpp ./lib/SVDLIBC/libsvd.a -O2 -o libSVDImp.so
g++ -fPIC -shared -I./lib/SVDLIBC/ -I${JNI_PATH} -I${JNI_PATH}/linux libSVD.cpp ./lib/SVDLIBC/libsvd.a -O2 -o libSVDImp.jnilib

#java -classpath "bin:lib/trove.jar" -Xmx6000m lowrankparser.LowRankParser  train train-file:data/$args.train.$type.$runid $@


