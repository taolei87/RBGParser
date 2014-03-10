#include <math.h>
#include <cstring>
#include "utils_SVD.h"

extern "C" {
#include "svdlib.h"
}

#define MAX_ITER 10000
#define EPSILON 1e-3

JNIEXPORT jdouble JNICALL Java_utils_SVD_powerMethod
  (JNIEnv *env, jclass obj, jintArray _x, jintArray _y, jdoubleArray _z, 
   jdoubleArray _u, jdoubleArray _v)
{
    jsize M = env->GetArrayLength(_x);
    jsize Nu = env->GetArrayLength(_u);
    jsize Nv = env->GetArrayLength(_v);
    int* x = env->GetIntArrayElements(_x, 0);
    int* y = env->GetIntArrayElements(_y, 0);
    double* z = env->GetDoubleArrayElements(_z, 0);
    double* u = env->GetDoubleArrayElements(_u, 0);
    double* v = env->GetDoubleArrayElements(_v, 0);
     
    double sigma = 0, invSigma;
    double prevSigma = -1;
    for (int i = 0; i < Nv; ++i) v[i] = 1.0;

    int iter;
    for (iter = 1; iter <= MAX_ITER; ++iter) {
        // u = Av
        for (int i = 0; i < Nu; ++i) u[i] = 0;
        for (int i = 0; i < M; ++i) u[x[i]] += z[i] * v[y[i]];
        
        // norm
        sigma = 0;
        for (int i = 0; i < Nu; ++i) {
            double x = u[i];
            if (x == 0) continue;
            sigma += x*x;
        }
        sigma = sqrt(sigma);
        invSigma = 1.0/sigma;
        for (int i = 0; i < Nu; ++i) if (u[i] != 0) u[i] *= invSigma;

        // v = M^Tu
        for (int i = 0; i < Nv; ++i) v[i] = 0;
        for (int i = 0; i < M; ++i) v[y[i]] += z[i] * u[x[i]];
        
        sigma = 0;
        for (int i = 0; i < Nv; ++i) {
            double x = v[i];
            if (x == 0) continue;
            sigma += x*x;
        }
        sigma = sqrt(sigma);

        if ((prevSigma != -1 && fabs(prevSigma - sigma) < EPSILON * prevSigma)) {
            break;
        }
        prevSigma = sigma;
    }
    invSigma = 1.0/sigma;
    for (int i = 0; i < Nv; ++i) v[i] *= invSigma;

    env->ReleaseIntArrayElements(_x, x, 0);
    env->ReleaseIntArrayElements(_y, y, 0);
    env->ReleaseDoubleArrayElements(_z, z, 0);
    env->ReleaseDoubleArrayElements(_u, u, 0);
    env->ReleaseDoubleArrayElements(_v, v, 0);

    return sigma;
}


//#define SVD_KAPPA 1e-4
//#define SVD_MAX_ITER 100
//
JNIEXPORT jint JNICALL Java_utils_SVD_svd__III_3I_3I_3D_3D_3D_3D
    (JNIEnv *env, jclass obj, jint _N, jint _M, jint _R, 
     jintArray _x, jintArray _y, jdoubleArray _z,
     jdoubleArray _S, jdoubleArray _Ut, jdoubleArray _Vt)
{
    SVDVerbosity = 0;
    int* x = env->GetIntArrayElements(_x, 0);
    int* y = env->GetIntArrayElements(_y, 0);
    double* z = env->GetDoubleArrayElements(_z, 0);
    jsize K = env->GetArrayLength(_x);
    int rank = 0;

    SMat smat = svdNewSMat(_N, _M, K);
    if (smat) {
        for (int i = 0, p = 0; i < _M; ++i) {
            smat->pointr[i] = p;
            while (p < K && y[p] == i) {
                smat->rowind[p] = x[p];
                smat->value[p] = z[p];
                ++p;
            }
        }
        smat->pointr[_M] = K;

        SVDRec rec = svdLAS2A(smat, _R);
        if (rec) {
            double* S = env->GetDoubleArrayElements(_S, 0);
            double* Ut = env->GetDoubleArrayElements(_Ut, 0);
            double* Vt = env->GetDoubleArrayElements(_Vt, 0);

            rank = rec->d;
            for (int i = 0; i < rank; ++i) S[i] = rec->S[i];
            for (int i = 0; i < rank; ++i)
                for (int j = 0; j < _N; ++j) Ut[i*_N+j] = rec->Ut->value[i][j];
            for (int i = 0; i < rank; ++i)
                for (int j = 0; j < _M; ++j) Vt[i*_M+j] = rec->Vt->value[i][j];

            env->ReleaseDoubleArrayElements(_S, S, 0);
            env->ReleaseDoubleArrayElements(_Ut, Ut, 0);
            env->ReleaseDoubleArrayElements(_Vt, Vt, 0);
        }
        svdFreeSMat(smat);
        svdFreeSVDRec(rec);
    }

    env->ReleaseIntArrayElements(_x, x, 0);
    env->ReleaseIntArrayElements(_y, y, 0);
    env->ReleaseDoubleArrayElements(_z, z, 0);

    return rank;
}

JNIEXPORT jint JNICALL Java_utils_SVD_lowRankSvd
    (JNIEnv *env, jclass obj, jdoubleArray _At, jdoubleArray _Bt, 
     jint _N, jint _M, jint _R, jdoubleArray _S, jdoubleArray _Ut, 
     jdoubleArray _Vt)
{
    
    //double svd_end[2] = { -1e-10, 1e-10 };

    SVDVerbosity = 0;
    double* At = env->GetDoubleArrayElements(_At, 0);
    double* Bt = env->GetDoubleArrayElements(_Bt, 0);

    DMat dAt = svdNewDMat(_R, _N);
    for (int i = 0; i < _R; ++i)
        for (int j = 0; j < _N; ++j)
            dAt->value[i][j] = At[i * _N + j];
    SMat sAt = svdConvertDtoS(dAt);
    svdFreeDMat(dAt);

    DMat dBt = svdNewDMat(_R, _M);
    for (int i = 0; i < _R; ++i)
        for (int j = 0; j < _M; ++j)
            dBt->value[i][j] = Bt[i * _M + j];
    SMat sBt = svdConvertDtoS(dBt);
    svdFreeDMat(dBt);

    env->ReleaseDoubleArrayElements(_At, At, 0);
    env->ReleaseDoubleArrayElements(_Bt, Bt, 0);

    //SVDRec Arec = svdLAS2(sAt, 0, SVD_MAX_ITER, svd_end, SVD_KAPPA);
    //SVDRec Brec = svdLAS2(sBt, 0, SVD_MAX_ITER, svd_end, SVD_KAPPA);
    SVDRec Arec = svdLAS2A(sAt, 0);
    SVDRec Brec = svdLAS2A(sBt, 0);
    svdFreeSMat(sAt);
    svdFreeSMat(sBt);
    
    if (Arec == NULL || Brec == NULL) {
        svdFreeSVDRec(Arec);
        svdFreeSVDRec(Brec);
        printf("WARNING: SVDLIBC las2a returns NULL \n\n");
        return 0;
    }

    int ranka = Arec->d, rankb = Brec->d;
    if (ranka == 0 || rankb == 0) {
        printf("WARNING: one matrix has 0 rank. %d %d\n\n", ranka, rankb);
        return 0;
    }

    DMat dM = svdNewDMat(ranka, rankb);
    for (int i = 0; i < ranka; ++i)
        for (int j = 0; j < rankb; ++j) {
            double va = 0;
            for (int k = 0; k < _R; ++k)
                va += Arec->Ut->value[i][k] * Brec->Ut->value[j][k];
            dM->value[i][j] = va * Arec->S[i] * Brec->S[j];
        }
    SMat sM = svdConvertDtoS(dM);
    svdFreeDMat(dM);
    //SVDRec Mrec = svdLAS2(sM, 0, SVD_MAX_ITER, svd_end, SVD_KAPPA);
    SVDRec Mrec = svdLAS2A(sM, 0);
    svdFreeSMat(sM);
    
    int rank = 0;
    if (Mrec != NULL && Mrec->d > 0) {

        double* S = env->GetDoubleArrayElements(_S, 0);
        double* Ut = env->GetDoubleArrayElements(_Ut, 0);
        double* Vt = env->GetDoubleArrayElements(_Vt, 0);

        rank = Mrec->d;
        for (int i = 0; i < rank; ++i) S[i] = Mrec->S[i];
        for (int i = 0; i < rank; ++i)
            for (int j = 0; j < _N; ++j) {
                int p = i * _N + j;
                for (int k = 0; k < ranka; ++k)
                    Ut[p] += Mrec->Ut->value[i][k] * Arec->Vt->value[k][j];
            }
        for (int i = 0; i < rank; ++i)
            for (int j = 0; j < _M; ++j) {
                int p = i * _M + j;
                for (int k = 0; k < rankb; ++k)
                    Vt[p] += Mrec->Vt->value[i][k] * Brec->Vt->value[k][j];
            }

        env->ReleaseDoubleArrayElements(_S, S, 0);
        env->ReleaseDoubleArrayElements(_Ut, Ut, 0);
        env->ReleaseDoubleArrayElements(_Vt, Vt, 0);

    } else {
        printf("WARNING: matrix M has 0 rank or las2a returns NULL \n\n");
    }

    svdFreeSVDRec(Arec);
    svdFreeSVDRec(Brec);
    svdFreeSVDRec(Mrec);
    
    return rank;
}

JNIEXPORT jint JNICALL Java_utils_SVD_svd___3DII_3D_3D_3D
    (JNIEnv *env, jclass obj, jdoubleArray _A, jint _N, jint _M, 
     jdoubleArray _S, jdoubleArray _Ut, jdoubleArray _Vt)
{
    
    //double svd_end[2] = { -1e-10, 1e-10 };

    SVDVerbosity = 0;
    double* At = env->GetDoubleArrayElements(_A, 0);

    DMat dAt = svdNewDMat(_N, _M);
    for (int i = 0; i < _N; ++i)
        for (int j = 0; j < _M; ++j)
            dAt->value[i][j] = At[i * _M + j];
    SMat sAt = svdConvertDtoS(dAt);
    svdFreeDMat(dAt);

    env->ReleaseDoubleArrayElements(_A, At, 0);
    
    jsize maxRank = env->GetArrayLength(_S);
    //SVDRec Arec = svdLAS2(sAt, 0, SVD_MAX_ITER, svd_end, SVD_KAPPA);
    SVDRec Arec = svdLAS2A(sAt, maxRank);
    svdFreeSMat(sAt);
    
    if (Arec == NULL) {
        svdFreeSVDRec(Arec);
        printf("WARNING: SVDLIBC las2a returns NULL \n\n");
        return 0;
    }

    int rank = Arec->d;

    double* S = env->GetDoubleArrayElements(_S, 0);
    double* Ut = env->GetDoubleArrayElements(_Ut, 0);
    double* Vt = env->GetDoubleArrayElements(_Vt, 0);

    for (int i = 0; i < rank; ++i) S[i] = Arec->S[i];

    for (int i = 0; i < rank; ++i)
        for (int j = 0; j < _N; ++j) {
            int p = i * _N + j;
            Ut[p] = Arec->Ut->value[i][j];
        }

    for (int i = 0; i < rank; ++i)
        for (int j = 0; j < _M; ++j) {
            int p = i * _M + j;
            Vt[p] += Arec->Vt->value[i][j];
        }

    env->ReleaseDoubleArrayElements(_S, S, 0);
    env->ReleaseDoubleArrayElements(_Ut, Ut, 0);
    env->ReleaseDoubleArrayElements(_Vt, Vt, 0);


    svdFreeSVDRec(Arec);
    
    return rank;
}


