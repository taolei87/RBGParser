RBGParser
=========

This project is developed at Natural Language Processing group in MIT. The project contains an Java implementation of a syntactic dependency parser with tensor decomposition, described in the following papers:

[1] Tao Lei, Yu Xin, Yuan Zhang, Regina Barzilay and Tommi Jaakkola. Low-Rank Tensors for Scoring Dependency Structures.  ACL 2014. [PDF](http://people.csail.mit.edu/taolei/papers/acl2014.pdf)

[2] Yuan Zhang, Tao Lei, Regina Barzilay, Tommi Jaakkola and Amir Globerson. Steps to Excellence: Simple Inference with Refined Scoring of Dependency Trees.  ACL 2014. [PDF](http://people.csail.mit.edu/yuanzh/papers/acl2014.pdf)

=========

##### 1. Compilation

To compile the project, first do a "make" in directory lib/SVDLIBC to compile the [SVD library](http://tedlab.mit.edu/~dr/SVDLIBC/). Next, make sure you have Java JDK installed on your machine and find the directory path of Java JNI include files. The directory should contains header files *jni.h* and *jni_md.h*. Take a look or directly use the shell script *make.sh* to compile the rest of the Java code. You have to replace the "jni_path" variable in *make.sh* with the correct JNI include path. Also, create a "bin" directory in the project directory before running *make.sh* script. 


##### 2. Data Format

The data format of this parser is the one used in CoNLL-X shared task, which describes a collection of annotated sentences (and the corresponding gold dependency structures). See more details of the format at [here](http://nextens.uvt.nl/depparse-wiki/DataFormat) and [here](https://code.google.com/p/clearparser/wiki/DataFormat#CoNLL-X_format_%28conll%29)


##### 3. Example Usage

