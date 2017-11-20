Given:

    https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.12/mockserver-netty-3.12-brew-tar.tar
    b14d3247ed35a298734e9e935a6b90d74c48b64eddd8810930f5b4ed62cd4492
    3.12

Then:

    wget https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.12/mockserver-netty-3.12-brew-tar.tar
    shasum -a 256 mockserver-netty-3.12-brew-tar.tar
    brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.12/mockserver-netty-3.12-brew-tar.tar --sha256=b14d3247ed35a298734e9e935a6b90d74c48b64eddd8810930f5b4ed62cd4492 

