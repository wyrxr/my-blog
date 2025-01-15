(ns my-blog.core
  (:require [markdown.core :as md]
            [hiccup.page :refer [html5]]
            [hiccup2.core :as h]
            [selmer.parser :as parser]
            [clojure.string :as str]
            [clojure.math :as math]
            [my-blog.server :refer [launch-site]])) 

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

(defn render-standalone-pages [path-to-content]
  (doseq [file (file-seq (clojure.java.io/file path-to-content))]
    (when (.endsWith (.getName file) ".md")
      (let [output-path (str "docs/" (.replace (.getName file) ".md" ".html"))]
        (render-page (md/md-to-html-string (slurp (.getPath file)))
                     @site-format
                     output-path))))) 

;; Render a post: The blog-post.html template expects a title, author, date, length, and content.
(defn render-post [content meta-info template output-path]
  (let [word-count (count (str/split content #" "))
        template-keys 
          (merge meta-info 
                 {:date-string (parse-date (meta-info :date)) 
                  :length word-count 
                  :content content 
                  :link (.replace output-path "docs/" "")})]
    (swap! posts conj template-keys)))

;; Creates posts based on the given path
;; Uses read-string function from clojure.core
;; Do not use on untrusted data
(defn generate-posts [post-path post-template]
  (doseq [file (file-seq (clojure.java.io/file post-path))]
    (when (.endsWith (.getName file) ".md")
      (let [output-path (str "docs/" (.replace (.getName file) ".md" ".html"))]
        (render-post (md/md-to-html-string (slurp (.getPath file))) 
                     (read-string (slurp (.replace (.getPath file) ".md" ".meta"))) 
                     post-template output-path))))
  (swap! site-format #(parser/render % {:post-archive (str "post-archive-" (int (math/floor (/ (count @posts) 10))) ".html")
                                               :content "{{content|safe}}"}))
  (dorun 
    (for [post @posts]
      (spit (str "docs/" (:link post)) (parser/render @site-format {:content (parser/render post-format post)})))))

  
(def blog-id (atom 0))

(defn paginate-post-archive [blurbs template script posts-per-page]
  (->> blurbs
     (partition posts-per-page posts-per-page nil)
     ((fn [pages]
        (let [page-count (count pages)]
          (loop [[page & remaining] (reverse pages) ;; to maintain stable URLs, post pages are numbered chronologically
                 page-number 0]
            (if page
              (let [navbar 
                     [:div {:class "text-block"} 
                      [:span
                       (if (zero? page-number)                        ;
                           [:span "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀ ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀0"]
                           [:a {:href "post-archive-0.html"} "<<-Oldest"])
                       (if-not (< page-number 2)
                               ;; MAGIC WHITESPACE
                               [:span "⠀⠀⠀" [:a {:href (str "post-archive-" (dec page-number) ".html")} "<-Older Posts···"]])
                       ;; EVIL WHITESPACE HACKS
                       (if (= page-number 1)
                           [:span "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀ ⠀⠀⠀⠀1"])
                       (if-not (< page-number 2) [:span (str (apply str (repeat (- 8 (count (str page-number))) "⠀")) page-number)])
                       (if-not (= page-number (dec page-count))
                               [:span {:style "float:right;"}
                                 (if-not (> page-number (- page-count 3))
                                         [:span [:a {:href (str "Post-archive-" (inc page-number) ".html")} "···Newer Posts->⠀"] "⠀⠀⠀"])
                                 [:a {:href (str "post-archive-" (dec page-count) ".html")} "Newest Posts->>"]])]]]
                (do (spit (str "docs/post-archive-" page-number ".html") (parser/render template {:content (html5 page navbar script)}))
                    (recur remaining (inc page-number)))))))))))

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
                   (html5 
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
                   "}")]]
    (paginate-post-archive post-blurbs (slurp "resources/templates/titlebar-and-theme.html") script 10)))    

(defn -main [& args]
  (generate-posts post-path post-format)
  (render-post-archive)
  (render-standalone-pages "resources/content/standalone")
  (if (some #{"server"} args) ;; "lein run server" will build the website and launch the server. 
    (do (println "Testing 1, 2, 3...")
        (launch-site))))
