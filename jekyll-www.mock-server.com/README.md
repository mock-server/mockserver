# install and run jekyll
```bash
# 1.
brew install rbenv
# 2.
rbenv install -l
# 3.
rbenv install 2.6.3
# 4.
rbenv local 2.6.3
# 5.
`rbenv which gem` install bundler
# 6.
`rbenv which bundle` install --path vendor/bundle
# 7.
`rbenv which bundle` exec jekyll serve
```

# deploy
```bash
# 1.
cd jekyll-www.mock-server.com
# 2.
rm -rf _site
# 3.
`rbenv which bundle` exec jekyll build
# 4.
open _site
# 5.
copy to main website S3 bucket (see ~/mockserver-aws-ids.md)
```

# slack
- Heroku Signup Application: https://dashboard.heroku.com/apps/join-mock-server-slack/settings
- Slackin: https://www.npmjs.com/package/slackin?activeTab=readme
- OLD: https://gitter.im/jamesdbloom/mockserver?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
