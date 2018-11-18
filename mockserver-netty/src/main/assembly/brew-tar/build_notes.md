Given:

    https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.5.0/mockserver-netty-5.5.0-brew-tar.tar
    5fa4a711c4b1fb3d9b5efbcc44d567b91a8d89e423e797057da9de66ef140c2d
    5.5.0
    https://github.com/Homebrew/homebrew-core/pull/23839

Then:

    wget https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.5.0/mockserver-netty-5.5.0-brew-tar.tar
    shasum -a 256 mockserver-netty-5.5.0-brew-tar.tar
    brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.5.0/mockserver-netty-5.5.0-brew-tar.tar --sha256=5fa4a711c4b1fb3d9b5efbcc44d567b91a8d89e423e797057da9de66ef140c2d 

