# create branch

1. git remote -v
1. git remote add upstream https://github.com/ORIGINAL_OWNER/ORIGINAL_REPOSITORY.git (i.e. git remote add upstream https://github.com/Homebrew/homebrew.git)
1. git remote -v
1. git fetch upstream
1. git checkout master
1. git merge upstream/master
1. git branch -D mockserver
1. git push origin --delete mockserver
1. git checkout -b mockserver
1. sub Library/Formula/mockserver.rb

# run build (following build steps are typically run on Jenkins when a Homebrew pull-request is tested)

1. brew update
1. mv /usr/local/Library/Formula/mockserver.rb /usr/local/Library/Formula/mockserver.rb_old
1. ln -s ~/git/homebrew/Library/Formula/mockserver.rb /usr/local/Library/Formula/mockserver.rb
1. brew doctor
1. brew --env
1. brew config
1. brew tests
1. brew readall --syntax
1. brew uninstall --force mockserver
1. brew uses mockserver
1. brew install --verbose --build-bottle mockserver
1. brew audit mockserver --strict
1. brew style mockserver
1. brew bottle --rb mockserver
1. brew uninstall --force mockserver
1. brew install ./mockserver-3.10.7.yosemite.bottle.tar.gz
1. brew test --verbose mockserver
1. brew uninstall --force mockserver
1. brew cleanup --prune=30
1. mv /usr/local/Library/Formula/mockserver.rb_old /usr/local/Library/Formula/mockserver.rb