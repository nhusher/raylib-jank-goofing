;; Builds the vendored raylib (vendor/raylib) as a shared library for jank.
;;
;; lein-jank runs this in a sandbox, passing build metadata as EDN on stdin
;; (bound to *input*). The source dir is read-only, so raylib is copied into
;; the scratch :build-dir, compiled there, and the artifacts are placed in
;; :out-dir. Anything printed as `jank-build::key=value` becomes a jank flag.
;;
;; A shared library is used because jank's JIT (`lein run`, the REPL) can only
;; load dynamic libraries.
(require '[babashka.fs :as fs]
         '[babashka.process :refer [shell]])

(let [{:keys [src-dir build-dir out-dir]} *input*
      raylib-src  (fs/path src-dir "vendor" "raylib" "src")
      work-dir    (fs/path build-dir "raylib")
      lib-dir     (fs/path out-dir "lib")
      include-dir (fs/path out-dir "include")
      cpus        (.availableProcessors (Runtime/getRuntime))]
  (fs/copy-tree raylib-src work-dir)
  (fs/create-dirs lib-dir)
  (fs/create-dirs include-dir)

  ;; Send make's chatter to stderr; stdout is reserved for directives.
  (shell {:dir (str work-dir) :out *err*}
         "make" (str "-j" cpus)
         "PLATFORM=PLATFORM_DESKTOP"
         "RAYLIB_LIBTYPE=SHARED"
         (str "RAYLIB_SRC_PATH=" work-dir)
         (str "RAYLIB_RELEASE_PATH=" lib-dir))

  (doseq [h ["raylib.h" "raymath.h" "rlgl.h"]]
    (fs/copy (fs/path raylib-src h) include-dir {:replace-existing true}))

  (println (str "jank-build::include-dir=" include-dir))
  (println (str "jank-build::link-dir=" lib-dir))
  (println "jank-build::link-library=raylib")
  ;; Paths relative to the project root. Without these, any change anywhere in
  ;; the project (including target/) would trigger a rebuild.
  (println "jank-build::rerun-if-changed=jank-build.bb")
  (println "jank-build::rerun-if-changed=vendor/raylib/src"))
