---
layout: post
title: Google Web Toolkit and Shiro
---

For the past two months we have been working on a relatively advanced integration project, this time using the GWT version of ObjectFabric. It helped fix tons of issues and moved the code a little more across the abyss which seems to separate “working” from “production ready”. It is starting to look good so I pushed the GWT port to GitHub. You can also find a few samples to get started.

This project included user management, conditional page and menu display based on roles, full-text search over a store of several million objects, and tab switching without page reload, with history support. We used EC2 as the backend, and Apache Shiro for authentication and authorization. All of those features should turn into OF samples at some point.

The Shiro integration was particularly valuable, and provides ObjectFabric with a good library for security. Shiro is mature, it became an Apache top level project in 2010, and impressive by its simplicity and power. Like ObjectFabric, it can be used for Web, mobile and desktop applications.