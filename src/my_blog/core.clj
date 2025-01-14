(ns my-blog.core
  (:require [markdown.core :as md]
            [hiccup.page :refer [html5]]
            [hiccup2.core :as h]
            [selmer.parser :as parser]
            [clojure.string :as str]
            [my-blog.server :refer [launch-site]])) 

;; A list to keep track of the aggregated posts
(def posts (atom (list)))

(def post-path "resources/content/posts")

;; Sitewide formatting template
(def site-format (slurp "resources/templates/titlebar-and-theme.html"))

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

(defn render-standalone-pages [path-to-content]
  (doseq [file (file-seq (clojure.java.io/file path-to-content))]
    (when (.endsWith (.getName file) ".md")
      (let [output-path (str "docs/" (.replace (.getName file) ".md" ".html"))]
        (render-page (md/md-to-html-string (slurp (.getPath file)))
                     site-format
                     output-path))))) 

;; Render a post: The blog-post.html template expects a title, author, date, length, and content.
(defn render-post [content meta-info template output-path]
  (let [word-count (count (str/split content #" "))
        template-keys 
          (merge meta-info 
                 {:date-string (parse-date (meta-info :date)) 
                  :length word-count 
                  :content content 
                  :link (.replace output-path "docs/" "")}) 
        html (parser/render template template-keys)]
    (swap! posts conj template-keys)
    (render-page html site-format output-path)))

;; Creates posts based on the given path
;; Uses read-string function from clojure.core
;; Do not use on untrusted data
(defn generate-posts [post-path post-template]
  (doseq [file (file-seq (clojure.java.io/file post-path))]
    (when (.endsWith (.getName file) ".md")
      (let [output-path (str "docs/" (.replace (.getName file) ".md" ".html"))]
        (render-post (md/md-to-html-string (slurp (.getPath file))) 
                     (read-string (slurp (.replace (.getPath file) ".md" ".meta"))) 
                     post-template output-path)))))

(def blog-id (atom 0))

(defn render-post-archive []
  (let [post-blurbs 
         (for [post (sort-by :date #(compare %2 %1) @posts)] ;; here we reverse the normal order of compare
                                                             ;; to get posts in reverse-chronological order
           (let [content (str/split (str/replace (str/replace (post :content) #"<p>" "") #"</p>" "<br><br>") #" ")]
             [:div {:class "text-block"}                       
               [:h1 [:a {:href (post :link)} (post :title)]]
               [:div {:class "meta-info"} (str (post :author) " · " (post :date-string) " · " (post :length) " words")]
               (apply str (interpose " " (take 100 content))) 
               (if (> (post :length) 100)
                 (let [blurb-id (swap! blog-id inc)] ;; IDs will be unique to the page
                   (h/html 
                     [:span {:id (str "more-" blurb-id) :style "display:none;"} 
                       (str " " (apply str (interpose " " (drop 100 content))))]
                     [:noscript
                       [:a {:href (post :link)} "..." [:br][:br] "(read more)"]
                       [:style (str "#link-" blurb-id " { display: none; }")]]
                     [:a {:href (str "javascript:showMore(" blurb-id ")") :id (str "link-" blurb-id)}
                         [:span {:id (str "ellipses-" blurb-id)} "..." [:br][:br]] ;; lovely span soup
                         [:span {:id (str "moreless-" blurb-id)} "(read more)"]])  ;; (don't eat)
                      ))]))
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
                   "}")] 
          html (parser/render (slurp "resources/templates/titlebar-and-theme.html") {:content (html5 post-blurbs script)})]
    (spit "docs/post-archive.html" html)))    

(defn -main [& args]
  (generate-posts post-path post-format)
  (render-post-archive)
  (render-standalone-pages "resources/content/standalone")
  (if (some #{"server"} args) ;; "lein run server" will build the website and launch the server. 
    (do (println "Testing 1, 2, 3...")
        (launch-site))))
