(ns my-blog.test
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route]))

(defn generate-routes [route-map]
  (apply compojure.core/routes
         (map #(GET (str "/" (:link %)) [] (slurp (str "output/" (:link %)))) route-map)))

(def static-routes
  [{:link "index.html"}
   {:link "post-archive.html"}])

(defn launch-site [route-map]
  (defroutes blog (generate-routes (into static-routes route-map))) 

  (run-jetty blog {:port 3000})) 
