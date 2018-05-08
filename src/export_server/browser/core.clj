(ns export-server.browser.core
  (:require [export-server.browser.etaoin.core :as etaoin-core]
            [export-server.browser.selenium.core :as selenium-core]
            [export-server.data.state :as state]))


;; PhantomJS troubles:
;; 1. openning local file url (file://big_file_with_bundle) - empty screenshot
;; 2. switch-window in Etaoin - https://github.com/igrishaev/etaoin/issues/146

;; Chrome troubles:
;; 1. take-screenshot in Selenium - hangs out
;; 2. open data url like (like data:base64,...) - limit to 2mb, but it's 3mb (Big AnyChart Bundle)

;; So there is some mix of using Selenium and Etaoin


(defn setup-drivers []
  (if (= :chrome (:engine @state/options))
    (etaoin-core/setup-drivers)
    (selenium-core/setup-drivers)))


(defn stop-drivers []
  (if (= :chrome (:engine @state/options))
    (etaoin-core/stop-drivers)
    (selenium-core/stop-drivers)))


(defn script-to-png [script quit-ph exit-on-error options type]
  (if (= :chrome (:engine @state/options))
    (etaoin-core/script-to-png script quit-ph exit-on-error options type)
    (selenium-core/script-to-png script quit-ph exit-on-error options type)))


(defn svg-to-png [svg quit-ph exit-on-error width height]
  (if (= :chrome (:engine @state/options))
    (etaoin-core/svg-to-png svg quit-ph exit-on-error width height)
    (selenium-core/svg-to-png svg quit-ph exit-on-error width height)))


(defn html-to-png [file quit-ph exit-on-error width height & [svg-type?]]
  (if (= :chrome (:engine @state/options))
    (etaoin-core/html-to-png file quit-ph exit-on-error width height svg-type?)
    (selenium-core/html-to-png file quit-ph exit-on-error width height svg-type?)))