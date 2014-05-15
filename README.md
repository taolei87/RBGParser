
### RBGParser
=========

This project is developed at Natural Language Processing group in MIT. The project contains a Java implementation of a syntactic dependency parser with tensor decomposition, described in the following papers:

[1] Tao Lei, Yu Xin, Yuan Zhang, Regina Barzilay and Tommi Jaakkola. Low-Rank Tensors for Scoring Dependency Structures.  ACL 2014. [PDF](http://people.csail.mit.edu/taolei/papers/acl2014.pdf)

[2] Yuan Zhang, Tao Lei, Regina Barzilay, Tommi Jaakkola and Amir Globerson. Steps to Excellence: Simple Inference with Refined Scoring of Dependency Trees.  ACL 2014. [PDF](http://people.csail.mit.edu/yuanzh/papers/acl2014.pdf)


This project is implemented by Tao Lei (taolei [at] csail.mit.edu) and Yuan Zhang (yuanzh [at] csail.mit.edu).

=========

<br>

##### 1. Compilation

To compile the project, first do a "make" in directory lib/SVDLIBC to compile the [SVD library](http://tedlab.mit.edu/~dr/SVDLIBC/). Next, make sure you have Java JDK installed on your machine and find the directory path of Java JNI include files. The directory should contains header files *jni.h* and *jni_md.h*. Take a look or directly use the shell script *make.sh* to compile the rest of the Java code. You have to replace the "jni_path" variable in *make.sh* with the correct JNI include path. Also, create a "bin" directory in the project directory before running *make.sh* script. 


<br> 

##### 2. Data Format

The data format of this parser is the one used in CoNLL-X shared task, which describes a collection of annotated sentences (and the corresponding gold dependency structures). See more details of the format at [here](http://nextens.uvt.nl/depparse-wiki/DataFormat) and [here](https://code.google.com/p/clearparser/wiki/DataFormat#CoNLL-X_format_%28conll%29)


<br>

##### 3. Example Usage

###### 3.1 Basic Usage

Take a look at *run.sh* as an example of running the parser. You could also run the parser for example as follows:

```
java -classpath "bin:lib/trove.jar" -Xmx20000m parser.DependencyParser \
  model-file:example.model \
  train train-file:example.train \
  test test-file:example.test
```

This will train a parser from the training data *example.train*, save the dependency model to the file *example.model* and evaluate this parser on the test data *example.test*.


###### 3.2 More Options

The parser will train a 3rd-order parser by default. To train a 1st-order (arc-based) model, run the parser like this:
```
java -classpath "bin:lib/trove.jar" -Xmx20000m parser.DependencyParser \
  model-file:example.model \
  train train-file:example.train \
  test test-file:example.test \
  model:basic
```
The argument ``model:MODEL-TYPE'' specifies the model type (basic: 1st-order features, standard: 3rd-order features and full: high-order global features).

There are many other possible running options. Here is a more complicated example:
```
java -classpath "bin:lib/trove.jar" -Xmx20000m parser.DependencyParser \
  model-file:example.model \
  train train-file:example.train \
  test test-file:example.test \
  model:standard  C:1.0  iters:5  pruning:false R:20 gamma:0.3 thread:4
```
This will run a standard model with regularization *C=1.0*, number of training iteration *iters=5*, rank of the tensor *R=20*, number of threads in parallel *thread=4*, weight of the tensor component *gamma=0.3*, and no dependency arc pruning *pruning=false*. You may take a look at RBGParser/src/parser/Options.java to see a full list of possible options.


###### 3.3 Using Word Embeddings

To add unsupervised word embeddings (word vectors) as auxiliary features to the parser. Use option "word-vector:WORD-VECTOR-FILE":
```
java -classpath "bin:lib/trove.jar" -Xmx20000m parser.DependencyParser \
  model-file:example.model \
  train train-file:example.train \
  test test-file:example.test \
  model:basic \
  word-vector:example.embeddings
```
The input file *example.embeddings* should be a text file specifying the real-value vectors of different words. Each line of the file should starts with the word, followed by a list of real numbers representing the vector of this word. For example:
```
this 0.01 0.2 -0.05 0.8 0.12
and 0.13 -0.1 0.12 0.07 0.03
to 0.11 0.01 0.15 0.08 0.23
*UNKNOWN* 0.04 -0.14 0.03 0.04 0
...
...
```
There may be a special word \*UNKNOWN\* used for OOV (out-of-vocabulary) word. Each line should contain the same number of real numbers. 
