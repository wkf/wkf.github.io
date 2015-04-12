(ns wkf.site
  (:require [goog.dom :as dom]
            [goog.dom.classes :as classes]
            [goog.style :as style]
            [goog.events :as events]
            [dommy.core :refer-macros [sel sel1] :as dommy]
            [FastClick]))

;; TODO:
;;   - avoid intro when coming back to site (maybe set cookie?)
;;   - fix elastic/rubber band scrolling
;;   - change copy every time you open the menu.
;;   - set timeout to remeasure scrollbar width
;;   - package fastclick for cljsjs
;;   - change link hover state to avoid fade (maybe come in from side?)

(defonce site
  (atom {:running? false
         :page-scroll 0
         :menu-scroll 0
         :menu-showing? false
         :menu-animating? false
         :line-height 0
         :scroll-width 0
         :content-width 0
         :page-thresholds
         {:fix-page-nav 0
          :fix-page-hr 0}
         :menu-thresholds
         {:fix-menu-nav 0
          :fix-menu-hr 0}}))

(def html (sel1 :html))
(def body (sel1 :body))

(def page (sel1 :.page))
(def page-content (sel1 :.content))
(def page-nav (sel1 page :nav))
(def page-ellipsis (sel1 page :.ellipsis))
(def page-header (sel1 page :header))
(def page-hr (sel1 page-header :hr))

(def menu (sel1 :.menu))
(def menu-content (sel1 menu :.content))
(def menu-nav (sel1 menu :nav))
(def menu-ellipsis (sel1 menu :.ellipsis))
(def menu-header (sel1 menu :header))
(def menu-hr (sel1 menu-header :hr))

(defn get-scroll []
  (let [scroll (dom/getDocumentScroll)]
    [(.-x scroll)
     (.-y scroll)]))

(defn get-size [el]
  (let [size (style/getSize el)]
    [(.-width size)
     (.-height size)]))

(defn px [n]
  (str n "px"))

(defn scroll! [x y]
  (.scroll js/window x y))

(defn measure-line-height
  "A hacky way to get the line height.
   Relies on the nav being 2 line-heights tall.
   Silly"
  []
  (-> page-nav style/getSize .-height (/ 2)))

(defn measure-scroll-width []
  (style/getScrollbarWidth))

(defn measure-window-width []
  (.-innerWidth js/window))

(defn measure-content-width [window-width scroll-width]
  (- window-width scroll-width))

(defn measure-page-thresholds [line-height]
  (let [[_ page-header-height] (get-size page-header)]
    {:fix-page-nav line-height
     :fix-page-hr (- page-header-height (* line-height 3))}))

(defn measure-menu-thresholds [line-height]
  (let [[_ menu-header-height] (get-size menu-header)]
    {:fix-menu-nav line-height
     :fix-menu-hr (- menu-header-height (* line-height 3))}))

(defn cache-measurements! []
  (let [line-height (measure-line-height)
        scroll-width (measure-scroll-width)
        window-width (measure-window-width)]
    (swap! site assoc
           :at-large? (>= window-width 860)
           :at-medium? (>= window-width 680)
           :at-small? (>= window-width 460)
           :line-height line-height
           :scroll-width scroll-width
           :window-width window-width
           :content-width (measure-content-width window-width scroll-width)
           :page-thresholds (measure-page-thresholds line-height)
           :menu-thresholds (measure-menu-thresholds line-height))))

(defn on-scroll [e]
  (let [{:keys [page-scroll
                menu-scroll
                menu-showing?
                menu-animating?]} @site
        [_ y] (get-scroll)
        [page-scroll'
         menu-scroll'] (cond
                         menu-animating? [page-scroll menu-scroll]
                         menu-showing? [page-scroll y]
                         :else [y menu-scroll])]
    (doseq [[c threshold] (:page-thresholds @site)]
      (if (>= page-scroll' threshold)
        (dommy/add-class! html c)
        (dommy/remove-class! html c)))
    (doseq [[c threshold] (:menu-thresholds @site)]
      (if (>= menu-scroll' threshold)
        (dommy/add-class! html c)
        (dommy/remove-class! html c)))))

(defn on-resize [e]
  (cache-measurements!)
  (doseq [el [page-content
              menu-content]]
    (style/setStyle
      el "width" (px (:content-width @site))))
  (on-scroll nil))

(defn wrap-prevent-default [f]
  (fn [e]
    (.preventDefault e)
    (f e)
    false))

(defn wrap-exact-target [f]
  (fn [e]
    (when (= (.-target e)
             (.-currentTarget e))
      (f e))))

(def transition-end
  #js["transitionend"
      "webkitTransitionEnd"
      "msTransitionEnd"
      "oTransitionEnd"])

(defn set-styles! [el & properties]
  (->> properties
       (apply js-obj)
       (style/setStyle el)))

(defn unset-styles! [el & properties]
  (->> (repeat "")
       (interleave properties)
       (apply js-obj)
       (style/setStyle el)))

(defn absolutize-menu-nav! [y]
  (set-styles! menu-nav
               "position" "absolute"
               "top" (px y)))

(defn absolutize-menu-hr! [y]
  (let [line-height (:line-height @site)
        top (+ y (* line-height 2))]
    (set-styles! menu-hr
                 "position" "absolute"
                 "top" (px top))))

(defn position! [el y]
  (set-styles! el "top" (px y)))

(defn unposition! [el]
  (unset-styles! el "top"))

(defn unabsolutize! [el]
  (unset-styles! el "position" "top"))

(defn on-click-page-ellipsis [e]
  (when-not (:menu-animating? @site)
    (let [[x y] (get-scroll)
          {:keys [menu-scroll]} @site]
      (swap! site assoc
             :page-scroll y
             :menu-animating? true)
      (position! page (- y))
      (dommy/add-class! html
                        :show-menu
                        :showing-menu)
      (unposition! menu)
      (scroll! x menu-scroll))))

(defn on-click-menu-ellipsis [e]
  (when-not (:menu-animating? @site)
    (let [[x y] (get-scroll)
          {:keys [at-large?
                  page-scroll
                  menu-thresholds]} @site
          {:keys [fix-menu-nav
                  fix-menu-hr]} menu-thresholds]
      (swap! site assoc
             :menu-scroll y
             :menu-animating? true)
      (when (>= y fix-menu-nav)
        (absolutize-menu-nav! y))
      (when (and  (>= y fix-menu-hr) (not at-large?))
        (absolutize-menu-hr! y))
      (dommy/add-class! html :hiding-menu)
      (position! menu (- y))
      (dommy/remove-class! html :show-menu)
      (unposition! page)
      (scroll! x page-scroll))))

(defn on-transition-end [e]
  (when (:menu-animating? @site)
    (swap! site assoc :menu-animating? false)
    (if (:menu-showing? @site)
      (do
        (dommy/remove-class! html :hiding-menu)
        (swap! site assoc :menu-showing? false))
      (do
        (unabsolutize! menu-nav)
        (unabsolutize! menu-hr)
        (dommy/remove-class! html :showing-menu)
        (swap! site assoc :menu-showing? true)))))

(def handlers
  [[js/window "resize" on-resize]
   [js/window "scroll" on-scroll]
   [menu transition-end (wrap-exact-target
                          on-transition-end)]
   [page-ellipsis "click" (wrap-prevent-default
                            on-click-page-ellipsis)]
   [menu-ellipsis "click" (wrap-prevent-default
                            on-click-menu-ellipsis)]])

(defn start
  "Start the site. Attempt to be idempotent."
  []
  (when-not (:running? @site)
    (swap! site assoc :running? true)
    (.attach js/FastClick (.-body js/document))
    (doseq [[el type f] handlers]
      (events/listen el type f))
    (on-resize nil)
    (on-scroll nil)))

(defn stop
  "Stop the site. Attempt to be idempotent. Useful for interactive local development."
  []
  (when (:running? @site)
    (reset! site {:running? false})
    (doseq [[el type _] handlers]
      (events/removeAll el type))))

(start)