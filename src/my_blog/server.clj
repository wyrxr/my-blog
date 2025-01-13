(ns my-blog.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route]
            [clojure.java.io :as io]))

(def root (str (System/getProperty "user.dir") "/docs"))

#_(defn generate-routes [route-map]
  (apply compojure.core/routes
         (map #(GET (str "/" %) [] (slurp (str "docs/" %))) route-map)))

(defroutes blog 
  (route/files "/" (do (println root) {:root root}))
  (route/not-found "404 File Not Found"))

(defn launch-site [route-map]
  #_(def files (map #(.getName %) (file-seq (io/file "docs"))))
  #_(println files)
  #_(defroutes blog (generate-routes files)) 

  (run-jetty blog {:port 3000})) 
