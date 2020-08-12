# install and run jekyll
1. `brew install rbenv`
1. `rbenv install -l`
1. `rbenv install 2.6.3`
1. `rbenv local 2.6.3`
1. `` `rbenv which gem` install bundler ``
1. `` `rbenv which bundle` install --path vendor/bundle``
1. `` `rbenv which bundle` exec jekyll serve``

# deploy
1. `` cd jekyll-www.mock-server.com``
1. `` rm -rf _site``
1. `` `rbenv which bundle` exec jekyll build``
1. `` open _site``
1. `` copy to https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/?region=us-east-1``

# slack
- Heroku Signup Application: https://dashboard.heroku.com/apps/join-mock-server-slack/settings
- Slackin: https://www.npmjs.com/package/slackin?activeTab=readme
- OLD: https://gitter.im/jamesdbloom/mockserver?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
