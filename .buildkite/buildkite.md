BuildKite Notes
===============

to trigger build using REST API:

    curl -u "jamesdbloom@gmail.com" https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds \
      -X POST \
      -F "commit=HEAD" \
      -F "branch=master" \
      -F "message=First build :rocket:"

build step (as docker command):

    # run as interactive container (for debugging)
    docker run -v /var/lib/buildkite-agent/builds/$BUILDKITE_AGENT_NAME:/build -i -t mockserver/mockserver:build /bin/bash

    # check files in build folder (for debugging)
    docker run -v /var/lib/buildkite-agent/builds/$BUILDKITE_AGENT_NAME/mockserver:/build -w /build/mockserver -a stdout -a stderr mockserver/mockserver:build ls -lrt

    # actually run the build
    docker run -v /var/lib/buildkite-agent/builds/$BUILDKITE_AGENT_NAME/mockserver:/build -w /build/mockserver -a stdout -a stderr mockserver/mockserver:build /build/mockserver/scripts/local_quick_build.sh

determine killed reason:

    dmesg | grep -E -i -B100 'killed process'

connecting to AWS EC2 instance:

    # check instance running
    aws ec2 get-console-output --instance-id i-04bc2edc8b4187ca8 --region us-east-1

    # ensure keypair has correct permissions
    chmod 400 ~/Downloads/mockserver-buildkite.pem

    # connect to EC2 linux instance using keypair and domain name
    ssh -i ~/Downloads/mockserver-buildkite.pem ec2-user@ec2-34-204-42-237.compute-1.amazonaws.com

    # connect to EC2 linux instance using keypair and ip address
    ssh -i ~/Downloads/mockserver-buildkite.pem ec2-user@52.91.13.160