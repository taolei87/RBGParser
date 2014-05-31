
import os, sys;

def main():
    lst = []
    for f in sys.argv[1:]:
        with open(f) as fin:
            for line in fin:
                parts = line.split();
                if len(parts) != 3: continue;
                t = [ int(x) for x in parts if x.isdigit() ];
                if len(t) != 3: continue;
                tt = t[1];
                t[1] = t[2];
                t[2] = tt;
                lst.append(t);
    print len(lst);
    lst = sorted(lst);
    #n = 3;
    #m = len(lst)/n;
    #st = 0;
    #ed = 0;
    lst2 = [ [], [] ];
    for i in range(len(lst)):
	if lst[i][0] <= 15:
		lst2[0].append(lst[i]);
	else:
		lst2[1].append(lst[i]);
        #ed = st + m;
        #if (n-i)*(m+1) == len(lst)-st:
        #    ed = ed + 1;
        #print st, ed;
        #lst2.append(lst[st:ed]);
        #st = ed;

    print len(lst2[0]), len(lst2[1]);
    for seg in lst2:
        nlen = sum(x[0] for x in seg) + 0.0;
        ua = sum(x[2] for x in seg) + 0.0;
        ntok = sum(x[1] for x in seg) + 0.0;
        print "%.1f\t%.2f" % (nlen/len(seg), ua/ntok*100);

if __name__ == "__main__":
    main();
