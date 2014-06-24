#!/usr/bin/env bash

export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'
export JAVA_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'
JAVA_VER=$(java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
current_directory=${PWD}

if [ $JAVA_VER -eq 16 ]; then
    echo
    echo "------------------------"
    echo "------- JAVA 1.6 -------"
    echo "------------------------"
    echo
    rm -rf $current_directory/target/travis
    git clone -b travis `git config --get remote.origin.url` $current_directory/target/travis
    for module in . core client integration-testing war proxy-war netty maven-plugin maven-plugin-integration-tests javascript examples; do
        cd $current_directory/mockserver-$module;
        echo
        echo ==== `pwd` ====;
        echo
        mvn deploy --settings $current_directory/target/travis/settings.xml
        echo
        cd $current_directory;
    done
    cd $current_directory/mockserver-ruby;
    echo
    echo ==== `pwd` ===;
    echo
    ruby --version
    rvm --version
    gem --version
    bundle --version
    export BUNDLE_GEMFILE=$PWD/Gemfile
    bundle install
    bundle exec rake
    echo
    cd $current_directory;
fi

if [ $JAVA_VER -eq 17 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.7 -----"
    echo "--------------------"
    echo
    for module in . core client integration-testing war proxy-war netty maven-plugin maven-plugin-integration-tests javascript examples; do
        cd $current_directory/mockserver-$module;
        echo
        echo ==== `pwd` ====;
        echo
        mvn install
        echo
        cd $current_directory;
    done
    cd $current_directory/mockserver-ruby;
    echo
    echo ==== `pwd` ===;
    echo
    ruby --version
    rvm --version
    gem --version
    bundle --version
    export BUNDLE_GEMFILE=$PWD/Gemfile
    bundle install
    bundle exec rake
    echo
    cd $current_directory;
fi

if [ $JAVA_VER -eq 18 ]; then
    echo
    echo "--------------------"
    echo "----- JAVA 1.8 -----"
    echo "--------------------"
    echo
    for module in . core client integration-testing war proxy-war netty maven-plugin maven-plugin-integration-tests javascript examples; do
        cd $current_directory/mockserver-$module;
        echo
        echo ==== `pwd` ====;
        echo
        mvn install
        echo
        cd $current_directory;
    done
    cd $current_directory/mockserver-ruby;
    echo
    echo ==== `pwd` ===;
    echo
    ruby --version
    rvm --version
    gem --version
    bundle --version
    export BUNDLE_GEMFILE=$PWD/Gemfile
    bundle install
    bundle exec rake
    echo
    cd $current_directory;
fi
