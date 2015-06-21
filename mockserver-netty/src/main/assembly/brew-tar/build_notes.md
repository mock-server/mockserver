The following build steps are typically run on Jenkins when a Homebrew pull-request is tested

1. ln -s ~/git/homebrew/Library/Formula/mockserver.rb /usr/local/Library/Formula/mockserver.rb
1. brew update
1. brew doctor
1. brew --env
1. brew config
1. brew tests
1. brew readall --syntax
1. brew uses mockserver
1. brew install --verbose --build-bottle mockserver
1. brew audit mockserver --strict
1. brew style mockserver
1. brew bottle --rb mockserver
1. brew uninstall --force mockserver
1. brew install ./mockserver-3.9.16.yosemite.bottle.tar.gz
1. brew test --verbose mockserver
1. brew uninstall --force mockserver
1. brew cleanup --prune=30