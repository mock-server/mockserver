import locust.stats
from locust import task, between

locust.stats.CONSOLE_STATS_INTERVAL_SEC = 60
# https://docs.locust.io/en/stable/increase-performance.html
from locust.contrib.fasthttp import FastHttpUser


class UserBehavior(FastHttpUser):
    wait_time = between(1, 1)

    @task
    def expectation(self):
        self.client.put("/mockserver/expectation",
                        "[{\n"
                        "    \"httpRequest\": {\n"
                        "        \"path\": \"/simple\"\n"
                        "    },\n"
                        "    \"httpResponse\": {\n"
                        "        \"statusCode\": 200,\n"
                        "        \"body\": \"some simple response\"\n"
                        "    },\n"
                        "    \"times\": {\n"
                        "        \"remainingTimes\": 5\n"
                        "    }\n"
                        "}]",
                        verify=False,
                        headers={
                            "Connection": "Keep-Alive",
                            "Keep-Alive": "timeout=120, max=1000"
                        })

    @task
    def request(self):
        self.client.get("/simple",
                        verify=False,
                        headers={
                            "Connection": "Keep-Alive",
                            "Keep-Alive": "timeout=120, max=1000"
                        })

    # @task
    # def forward(self):
    #     self.client.get("/forward")
