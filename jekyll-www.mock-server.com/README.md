# MockServer Documentation Site

> [Jekyll](https://jekyllrb.com/) source for [www.mock-server.com](https://www.mock-server.com)

## Development

Use the dev server script (handles Ruby version, gem installation, and live reload):

```bash
./jekyll_server.sh
```

This requires [rbenv](https://github.com/rbenv/rbenv) with Ruby 3.3.11 installed:

```bash
brew install rbenv
rbenv install 3.3.11
```

## Manual Build

```bash
cd jekyll-www.mock-server.com
rbenv local 3.3.11
$(rbenv which bundle) config set --local path vendor/bundle
$(rbenv which bundle) install
$(rbenv which bundle) exec jekyll build
```

The built site is output to `_site/`.

## Deploy

The site is hosted on S3 + CloudFront. See [docs/operations/website.md](../docs/operations/website.md) for deployment details.

## Community, Issues & Contributing

See the [main MockServer README](../README.md) for community links, how to report issues, and contribution guidelines.
