(ns my-blog.core
  (:require [markdown.core :as md]
            [hiccup.page :refer [html5]]
            [hiccup2.core :as h]
            [selmer.parser :as parser]
            [clojure.string :as str]
            [clojure.math :as math]
            [clojure.java.io :refer [make-parents]]
            [my-blog.server :refer [launch-site]]
            [clojure.java.shell :refer [sh]])) 

;; A list to keep track of the aggregated posts
(def posts (atom (list)))

(def post-path "resources/content/posts")

;; Sitewide formatting template
(def site-format (atom (slurp "resources/templates/titlebar-and-theme.html")))

;; Format for the individual blog posts
(def post-format (slurp "resources/templates/blog-post.html"))

(def months {1 "Jan" 2 "Feb" 3 "Mar" 4  "Apr"  5 "May"  6 "Jun"
             7 "Jul" 8 "Aug" 9 "Sep" 10 "Oct" 11 "Nov" 12 "Dec"})

(defn parse-date [[year month day hour minute]]
 (str (months month) ". " day ", " year " at " hour ":" minute))

;; expects a markdown string, a template, and an output file 
(defn render-page [content template output-path]
  (let [html (parser/render template {:content content})]
    (spit output-path html))) 

;; This function is VERY similar to the render-standalone-pages function,
;; but it has a slightly different handling of files, which makes it clunky
;; to create a generalized solution. 
(defn render-pages [path-to-content template extension output-dir]
  (doseq [file (file-seq (clojure.java.io/file path-to-content))]
    (when (.endsWith (.getName file) extension)
      (let [output-path (.replace (.getName file) extension ".html")]
        (let [html (parser/render template (read-string (slurp (.getPath file))))]
          (spit (str output-dir output-path) html))))))

(defn render-standalone-pages [path-to-content]
  (doseq [file (file-seq (clojure.java.io/file path-to-content))]
    (when (.endsWith (.getName file) ".md")
      (let [output-path (.replace (.getName file) ".md" ".html")]
        (render-page (md/md-to-html-string (slurp (.getPath file)))
                     @site-format
                     (str "docs/" output-path)))))) 

;; Render a post: The blog-post.html template expects a title, author, date, length, and content.
(defn render-post [content meta-info template output-path]
  (let [word-count (count (str/split content #" "))
        template-keys 
          (merge meta-info 
                 {:date-string (parse-date (meta-info :date)) 
                  :length word-count 
                  :content content 
                  ; :link (.replace output-path "docs" "/")
                  :link output-path
                  :tags-formatted
                  (str "tags: " 
                       (h/html (map #(identity [:li [:a {:href (str "/my-blog/tags/" % "/post-archive-0.html")} %]]) 
                                         (or (meta-info :tags) ["untagged"]))))
                  :tags (or (meta-info :tags) ["untagged"])})]

    (swap! posts conj template-keys)
    (make-parents (:link template-keys))
    (spit (str "docs/" (:link template-keys)) (parser/render @site-format {:content (parser/render post-format template-keys)}))))

;; Creates posts based on the given path
;; Uses read-string function from clojure.core
;; Do not use on untrusted data
(defn generate-posts [post-path post-template output-directory]
  (do
  (doseq [file (file-seq (clojure.java.io/file post-path))]
    (when (.endsWith (.getName file) ".md")
      (let [output-path (str output-directory (.replace (.getName file) ".md" ".html"))]
        (render-post (md/md-to-html-string (slurp (.getPath file))) 
                     (read-string (slurp (.replace (.getPath file) ".md" ".meta"))) 
                     post-template output-path))))
    (swap! site-format #(parser/render % {:post-archive (h/html [:a {:href (str "/my-blog/posts/post-archive-" (int (math/floor (/ (count @posts) 10))) ".html")} "Posts"])
                                               :content "{{content|safe}}"}))))
        
(def blog-id (atom 0))

(defn paginate-post-archive [blurbs output-location template script posts-per-page archive-title]
  (->> blurbs
     (partition posts-per-page posts-per-page nil)
     ((fn [pages]
        (let [page-count (count pages)
             archive-title 
             [:div {:class "archive-title"} archive-title]]
          (loop [[page & remaining] (reverse pages) ;; to maintain stable URLs, post pages are numbered chronologically
                 page-number 0]
            (if page
              (let [navbar 
                     [:div {:class "text-block"} 
                      [:span
                       (if (zero? page-number)                        ;
                           [:span "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀ ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀0"]
                           [:a {:href (str "/my-blog/" output-location "post-archive-0.html")} "<<-Oldest"])
                       (if-not (< page-number 2)
                               ;; MAGIC WHITESPACE
                               [:span "⠀⠀⠀" [:a {:href (str "/my-blog/" output-location "post-archive-" (dec page-number) ".html")} "<-Older Posts···"]])
                       ;; EVIL WHITESPACE HACKS
                       (if (= page-number 1)
                           [:span "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀ ⠀⠀⠀⠀1"])
                       (if-not (< page-number 2) [:span (str (apply str (repeat (- 8 (count (str page-number))) "⠀")) page-number)])
                       (if-not (= page-number (dec page-count))
                               [:span {:style "float:right;"}
                                 (if-not (> page-number (- page-count 3))
                                         [:span [:a {:href (str "/my-blog/" output-location "post-archive-" (inc page-number) ".html")} "···Newer Posts->⠀"] "⠀⠀⠀"])
                                 [:a {:href (str "/my-blog/" output-location "post-archive-" (dec page-count) ".html")} "Newest Posts->>"]])]]]
                (do (make-parents (str "docs/" output-location "post-archive-" page-number ".html")) 
                    (spit (str "docs/" output-location "post-archive-" page-number ".html") (parser/render @site-format {:content (html5 archive-title page navbar script)}))
                    (recur remaining (inc page-number)))))))))))

(defn render-post-archive [output-location posts archive-title]
  (let [post-blurbs 
         (for [post (sort-by :date #(compare %2 %1) posts)] ;; here we reverse the normal order of compare
                                                             ;; to get posts in reverse-chronological order
           
           (html5                                                 
            (let [content (str/split (str/replace (str/replace (post :content) #"<p>" "") #"</p>" "<br><br>") #" ")]
             [:div {:class "text-block"}                       
               [:h1 [:a {:href (str "/my-blog" (post :link))} (post :title)]]
               [:div {:class "meta-info"} (str (post :author) " · " (post :date-string) " · " (post :length) " words")]
               (apply str (interpose " " (take 100 content))) 
               (if (> (post :length) 100)
                 (let [blurb-id (swap! blog-id inc)] ;; IDs will be unique to the page
                   [:span
                     [:span {:id (str "more-" blurb-id) :style "display:none;"} 
                       (str " " (apply str (interpose " " (drop 100 content))))]
                     [:noscript
                       [:a {:href (str "/my-blog/" (post :link))} "..." [:br][:br] "(read more)"]
                       [:style (str "#link-" blurb-id " { display: none; }")]]
                     [:a {:href (str "javascript:showMore(" blurb-id ")") :id (str "link-" blurb-id)}
                         [:span {:id (str "ellipses-" blurb-id)} "..." [:br][:br]] ;; lovely span soup
                         [:span {:id (str "moreless-" blurb-id)} "(read more)"]]]   ;; (don't eat)
                       ))
                [:div {:class "tags"}
                  (post :tags-formatted)]])))
          ;; Yes, we're writing inline JavaScript. 
          script [:script (str 
                   "function showMore(id) {\n"
                   "    const link = document.getElementById(`link-${id}`);\n"
                   "    const more = document.getElementById(`more-${id}`);\n"
                   "    const moreLessTag = document.getElementById(`moreless-${id}`);\n"
                   "    const ellipses = document.getElementById(`ellipses-${id}`);\n"
                   "    if (more.style.display === 'none') {\n"
                   "        moreLessTag.textContent = '(show less)';\n"
                   "        more.style.display = 'inline'; // Show the hidden content\n"
                   "        ellipses.style.display = 'none';\n"
                   "    } else {\n"
                   "        ellipses.style.display = 'inline';\n"
                   "        moreLessTag.textContent = '(read more)';\n"
                   "        link.style.display = 'inline';\n"
                   "        more.style.display = 'none';\n"
                   "    }\n"
                   "}")]]
    (paginate-post-archive post-blurbs output-location @site-format script 10 archive-title)))    

(defn -main [& args]
  (generate-posts post-path post-format "/posts/")
  (render-post-archive "posts/" @posts "All Posts")
  (->> @posts
       (map :tags)
       (flatten)
       (set)
       (map #(identity [% (filter (fn [x] (some #{%} (:tags x))) @posts)]))
       (into {})
       (map (fn [[tag tagged-posts]] (render-post-archive (str "tags/" tag "/") tagged-posts (str "Posts tagged '" tag "'"))))
       (dorun))
  (->> @posts
       (map #(spit (str "docs/" (:link %)) (parser/render @site-format {:content (parser/render post-format %)})))
       (dorun))
  (render-standalone-pages "resources/content/standalone")
  (render-pages "resources/content/image-details" 
                (parser/render @site-format {:content (slurp "resources/templates/image-details.html")})
                ".edn"
                "docs/image-details/")
  (if (some #{"server"} args) ;; "lein run server" will build the website and launch the server. 
    (do (println "Testing 1, 2, 3...")
        (sh "rm" "-rf" "server/my-blog")
        (sh "cp" "-r" "docs" "server/my-blog")
        (launch-site))))
