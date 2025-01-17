(defproject my-blog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/wyrxr/my-blog"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [markdown-clj "1.10.0"]
                 [hiccup "2.0.0-RC4"]
                 [selmer "1.12.61"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [compojure "1.6.2"]]
  :main ^:skip-aot my-blog.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
