(defproject datomic-free "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [enlive "1.1.5"]
                 [clj-http "0.7.8"]
                 [me.raynes/fs "1.4.4"]]
  :main ^:skip-aot datomic-free.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
