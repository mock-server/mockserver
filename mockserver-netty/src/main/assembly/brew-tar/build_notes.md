Given:

    https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.5.0/mockserver-netty-5.5.0-brew-tar.tar
    323fe846d9a6e47d508d26a428b5d6748572b814569aecdd65916dc7279f1308
    5.5.0
    https://github.com/Homebrew/homebrew-core/pull/23839

Then:

    wget https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.5.0/mockserver-netty-5.5.0-brew-tar.tar
    shasum -a 256 mockserver-netty-5.5.0-brew-tar.tar
    brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.5.0/mockserver-netty-5.5.0-brew-tar.tar --sha256=323fe846d9a6e47d508d26a428b5d6748572b814569aecdd65916dc7279f1308 

