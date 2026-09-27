#include <iostream>
using namespace std;
int main(){
    int n,d;cin>>n>>d;
    int x[105];
    for(int i=1;i<=n;i++){
        cin>>x[i];
    }
    for(int i=1;i<=n;i++){
        bool f=1;
        for(int j=1;j<=n;j++){
            if(i==j)continue;
            if(abs(x[i]-x[j])<d){
                f=0;
                break;
            }
        }
        if(f)cout<<i<<" ";
    }
}
