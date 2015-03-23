
### RBGParser v1.1

This version improves parsing speed using the hash kernel (see [4]) and by optimizing the code. We also improved the unlabeled attachment score (UAS) slightly and labeled attachment score (LAS) significantly. 
  * feature index lookup: use hash kernel (i.e. ignoring collisions) instead of a look-up table
  * dependency labels: now use a complete set of first-order features; will consider adding rich features later
  * online update method: a slightly modified version
  * optimized feature cache at code level
  * now can prune low-frequent labels, words, etc.

=========

#### About and Contact

This project is developed at Natural Language Processing group in MIT. It contains a Java implementation of a syntactic dependency parser with tensor decomposition and greedy decoding, described in [1,2,3].

This project is implemented by Tao Lei (taolei [at] csail.mit.edu) and Yuan Zhang (yuanzh [at] csail.mit.edu).

=========

#### [Quick Start](https://github.com/taolei87/RBGParser/wiki/Quick-Start)


======

#### References

[1] Tao Lei, Yu Xin, Yuan Zhang, Regina Barzilay and Tommi Jaakkola. Low-Rank Tensors for Scoring Dependency Structures.  ACL 2014. [PDF](http://people.csail.mit.edu/taolei/papers/acl2014.pdf)

[2] Yuan Zhang, Tao Lei, Regina Barzilay, Tommi Jaakkola and Amir Globerson. Steps to Excellence: Simple Inference with Refined Scoring of Dependency Trees.  ACL 2014. [PDF](http://people.csail.mit.edu/yuanzh/papers/acl2014.pdf)

[3] Yuan Zhang\*, Tao Lei\*, Regina Barzilay and Tommi Jaakkola. Greed is Good if Randomized: New Inference for Dependency Parsing. EMNLP 2014. [PDF](http://people.csail.mit.edu/taolei/papers/emnlp2014.pdf)

[4] Bernd Bohnet. Very High Accuracy and Fast Dependency Parsing is not a Contradiction. The 23rd International Conference on Computational Linguistics. COLING 2010. [PDF](http://anthology.aclweb.org/C/C10/C10-1011.pdf)
