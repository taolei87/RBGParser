import os, sys;

TRAIN = "./data/english08.train.lab";
TEST = "./data/english08.test.lab";

TRAIN1 = "./runs/english08-semi1-%s.train.lab";
TEST1  = "./runs/english08-semi1-%s.test.lab";
OUT1   = "./runs/english08-semi1-%s.out";
MODEL1 = "./runs/english08-semi1-%s.model";

TRAIN2 = "./runs/english08-semi2-%s.train.lab";
#TEST2  = "./runs/english08-semi2-%s.test.lab";
OUT2   = "./runs/english08-semi2-%s.out";
MODEL2 = "./runs/english08-semi2-%s.model";

TRAINCMD = "java -cp 'bin:lib/trove.jar' -Xmx20000m parser.DependencyParser model-file:%s " \
        + "train train-file:%s %s";

TRAINCMD2= "java -cp 'bin:lib/trove.jar' -Xmx20000m parser.DependencyParser model-file:%s " \
        + "train train-file:%s test test-file:%s output-file:%s %s";

PREDICTCMD = "java -cp 'bin:lib/trove.jar' -Xmx20000m parser.DependencyParser model-file:%s " \
        + "test test-file:%s output-file:%s";

TESTCMD = "java -cp 'bin:lib/trove.jar' -Xmx20000m parser.DependencyParser model-file:%s " \
        + "test test-file:%s %s";


def read_conll(path):
    corpus = [ ];
    with open(path) as fin:
        s = [ ];
        for line in fin:
            line = line.strip();
            if line:
                s.append(line);
            elif len(s) > 0:
                corpus.append(s);
                s = [ ];
    return corpus;

def write_conll(path, corpus):
    with open(path, "w") as fout:
        for s in corpus:
            for l in s:
                fout.write(l+"\n");
            fout.write("\n");

def main():
    if len(sys.argv) < 4:
        print "sstrain.py runid n m step:[1-2] [other arguments]";
        return;

    runid = sys.argv[1];
    n = int(sys.argv[2]);
    m = int(sys.argv[3]);
    args = " ".join(sys.argv[4:]);

    if ("step:1" in sys.argv) or ("step:2" not in sys.argv):
        corpus = read_conll(TRAIN);

        model1 = MODEL1 % runid;
        train1 = TRAIN1 % runid;
        #test1  = TEST1 % runid;
        #out1   = OUT1 % runid;
        write_conll(train1, corpus[:n]);
        #write_conll(test1, corpus[n:n+m]);
        runcmd = TRAINCMD % (model1, train1, args);
        os.system(runcmd);

        runcmd = TESTCMD % (model1, TEST, args);
        os.system(runcmd);


    else:
        corpus = read_conll(TRAIN);

        model1 = MODEL1 % runid;
        train1 = TRAIN1 % runid;
        test1  = TEST1 % runid;
        out1   = OUT1 % runid;
        #write_conll(train1, corpus[:n]);
        write_conll(test1, corpus[n:n+m]);
        runcmd = PREDICTCMD % (model1, test1, out1);
        os.system(runcmd);

        train2 = TRAIN2 % runid;
        os.system("cat %s %s > %s" % (train1, out1, train2));

        model2 = MODEL2 % runid;
        train2 = TRAIN2 % runid;
        test2 = TEST;
        out2 = OUT2 % runid;

        runcmd = TRAINCMD2 % (model2, train2, test2, out2, args);
        os.system(runcmd);

        #runcmd = TESTCMD % (model2, TEST, args);
        #os.system(runcmd);



if __name__ == "__main__":
    main();
