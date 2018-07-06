load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "sc-gerrit-timestamps",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: sc-gerrit-timestamps",
    ],
    resources = glob(["src/main/resources/**/*"]),
)
