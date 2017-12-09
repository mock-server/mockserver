Given:

    https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.1.1/mockserver-netty-5.1.1-brew-tar.tar
    7fc77904986e6fc5c17c7a3b25af3d0a45e7e94d8fc0de62f3126efb68a3ed91
    5.1.1

Then:

    wget https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.1.1/mockserver-netty-5.1.1-brew-tar.tar
    shasum -a 256 mockserver-netty-5.1.1-brew-tar.tar
    brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.1.1/mockserver-netty-5.1.1-brew-tar.tar --sha256=7fc77904986e6fc5c17c7a3b25af3d0a45e7e94d8fc0de62f3126efb68a3ed91 

