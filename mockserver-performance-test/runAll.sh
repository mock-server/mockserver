#!/usr/bin/env bash

./standalone_performance_test_memory_size.sh 2>&1 > standalone_performance_test_memory_size.log

sleep 120

./standalone_performance_test_thread_loop_size.sh 2>&1 > standalone_performance_test_thread_loop_size.log