Given:

    https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.12/mockserver-netty-3.12-brew-tar.tar
    f381d0318c8cb8be04e7d18c117ebcf91b48228cb0bf8783311a38f268311fe5
    3.12

Then:

    wget https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.12/mockserver-netty-3.12-brew-tar.tar
    shasum -a 256 mockserver-netty-3.12-brew-tar.tar
    brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.12/mockserver-netty-3.12-brew-tar.tar --sha256=f381d0318c8cb8be04e7d18c117ebcf91b48228cb0bf8783311a38f268311fe5 --version=3.12 

