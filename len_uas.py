
import os, sys;

def main():
    lst = []
    print os.sys[1];
    with open(os.sys[1]) as fin:
        for line in fin:
            parts = line.split();
            if len(parts) != 3: continue;
            t = [ int(x) for x in parts if x.isdigit ];
            if len(t) != 3: continue;
            lst.append(t);
    print len(lst);
    lst = sorted(lst);
    n = 10;
    m = len(lst)/n;
    st = 0;
    ed = 0;
    lst2 = [];
    for i in range(n):
        ed = st + m;
        if (n-i)*(m+1) == len(lst)-st:
            ed = ed + 1;
        print st, ed;
        lst2.append(lst[st:ed]);

if __name__ == "__main__":
    main();
