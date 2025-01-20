(ns my-blog.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route]
            [clojure.java.io :as io]))

;; GitHub pages deploys from the /docs folder: This mimics that behavior.
;; Currently used for local testing--presumably this could be deployed
;; on a server you control.
(def root (str (System/getProperty "user.dir") "/server"))

(defn launch-site []
  (defroutes blog
    (route/files "/" (do (println root) {:root root}))
    (route/not-found (slurp "server/my-blog/404.html")))
  (run-jetty blog {:port 3000})) 
