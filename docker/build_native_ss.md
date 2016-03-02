To rebuild `libtcnative-1.so` and `tomcat-native-1.1.27.jar` use the following steps

```bash
mkdir native_ssl
docker run -i -t -v `pwd`/native_ssl:/native_ssl java /bin/bash
cd /native_ssl
git clone https://github.com/twitter/finagle.git
apt-get update
apt-get install -y build-essential patch libapr1 libapr1-dev openssl libssl-dev ant
cd finagle/finagle-native/
./grab_and_patch_tomcat_native.sh
cd tomcat-native-1.1.27-src/jni/native/
./configure --with-apr=/usr/bin/apr-1-config --with-java-home=$JAVA_HOME --with-ssl=yes
make && make install
cd ..
ant jar
cp /usr/local/apr/lib/libtcnative-1.so.0.1.27 /native_ssl/
cp ./dist/tomcat-native-1.1.27.jar /native_ssl/
rm -rf /native_ssl/finagle/
exit
```

To test use Apache Bench

```bash
apt-get install apache2 apache2-utils
curl 'http://172.17.0.2:1080/expectation' -X PUT -H 'Content-Type: application/json' -d '{"httpRequest":{"method":"GET","path":"/simple"},"httpResponse":{"statusCode":200,"body":"some response"},"times":{"remainingTimes":1,"unlimited":true}}'
ab -n 10000 -c 100 -Z 'AES128-SHA' https://172.17.0.2:1080/simple
```