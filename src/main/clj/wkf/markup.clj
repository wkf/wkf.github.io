(ns wkf.markup
  (:require [endophile.core :as endophile]
            [net.cgrand.tagsoup :as tagsoup]
            [net.cgrand.enlive-html :as html :refer [html]]))

(def defaults
  {:title "Will Farrell"
   :styles ["css/out/screen.css"
            "//cloud.typography.com/6114452/688926/css/fonts.css"]
   :scripts ["js/out/main.js"]})

(defn html5 [& nodes]
  (apply html {:type :dtd :data ["html"]} nodes))

(defn render [nodes] (apply str nodes))

(defn markdown-parser [stream]
  (html/with-options
    {:parser tagsoup/parser}
    (-> stream slurp endophile/mp endophile/to-clj)))

(html/deftemplate page
  (html5
    [:html
     [:head
      [:meta
       {:name "viewport"
        :content "width=device-width, initial-scale=1"}]
      [:title]]
     [:body
      [:section.page
       [:div.content
        [:nav
         [:a.parens
          [:span "( )"]]
         [:a.ellipsis
          [:span "..."]]]
        [:header
         [:h1 "Will Farrell"]
         [:h2
          [:span "Hello. "]
          [:span "I'm a developer. "]
          [:em "It's nice."]]
         [:hr]]
        [:main]
        [:footer
         [:hr]
         [:small "(c) 2015 Will Farrell"]]]]
      [:section.menu
       [:div.content
        [:nav
         [:a.parens
          [:span "( )"]]
         [:a.ellipsis
          [:span "..."]]]
        [:header
         [:h1 "Will Farrell"]
         [:h2
          [:span "Hit me up. "]
          [:em "Seriously."]]
         [:hr]]
        [:main
         [:h3 "GitHub"]
         [:small [:em "Take a look at some of my work."]]
         [:h3 "Twitter"]
         [:small [:em "Say something publicly."]]
         [:h3 "Email"]
         [:small [:em "Say something privately (it can be our secret)."]]]
        [:footer
         [:hr]
         [:small "(c) 2015 Will Farrell"]]]]]])

  [{:keys [title scripts requires styles]} & [main]]

  [:title] (html/content title)
  [:a.parens] (html/set-attr :href "/")
  [:a.ellipsis] (html/set-attr :href "#")
  [:head] (html/prepend
            (map
              #(html [:link {:type "text/css" :rel "stylesheet" :href %}]) styles))
  [:body] (html/append
            (map
              #(html [:script {:type "text/javascript" :src %}]) scripts)
            (map
              #(html [:script {:type "text/javascript"} (str "goog.require('" % "')")]) requires))
  [:.page :main] (html/content main))

(html/defsnippet home
  {:parser markdown-parser} "content/home.md"
  [html/root] [])

(defn manifest [config]
  (let [config (merge defaults config)]
    {"" #(render (page config (home)))}))