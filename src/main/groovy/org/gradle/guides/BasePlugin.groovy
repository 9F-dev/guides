/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.guides

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.ajoberstar.gradle.git.publish.GitPublishExtension
import org.asciidoctor.gradle.AsciidoctorBackend
import org.asciidoctor.gradle.AsciidoctorTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.PathSensitivity

/**
 * The guides base plugin provides conventions for all Gradle guides.
 * <p>
 * Adds the custom attributes to {@link org.asciidoctor.gradle.AsciidoctorTask} for reference in Asciidoc files:
 * <ul>
 *     <li>{@literal samplescodedir}: The directory containing samples code defined as {@literal "$projectDir/samples/code"}</li>
 *     <li>{@literal samplescodedir}: The directory containing expected samples output defined as {@literal "$projectDir/samples/output"}</li>
 * </ul>
 */
@CompileStatic
class BasePlugin implements Plugin<Project> {

    static final String GUIDE_EXTENSION_NAME = 'guide'
    static final String CHECK_LINKS_TASK = 'checkLinks'

    void apply(Project project) {
        project.apply plugin : org.gradle.api.plugins.BasePlugin

        addGuidesExtension(project)
        addGradleRunnerSteps(project)
        addAsciidoctor(project)
        addGithubPages(project)
        addCloudCI(project)
        addCheckLinks(project)
    }

    private void addGuidesExtension(Project project) {
        project.extensions.create(GUIDE_EXTENSION_NAME,GuidesExtension,project)
    }

    private void addGradleRunnerSteps(Project project) {
        project.apply plugin : 'org.ysb33r.gradlerunner'
    }

    private void addCheckLinks(Project project) {
        CheckLinks task = project.tasks.create(CHECK_LINKS_TASK,CheckLinks)

        AsciidoctorTask asciidoc = (AsciidoctorTask)(project.tasks.getByName('asciidoctor'))
        task.indexDocument = {  project.file("${asciidoc.outputDir}/html5/index.html") }
        task.dependsOn asciidoc
    }

    private void addAsciidoctor(Project project) {

        String gradleVersion = project.gradle.gradleVersion

        project.apply plugin: 'org.asciidoctor.convert'

        AsciidoctorTask asciidoc = (AsciidoctorTask) (project.tasks.getByName('asciidoctor'))
        project.tasks.getByName('build').dependsOn asciidoc

        Task asciidocAttributes = project.tasks.create('asciidoctorAttributes')
        asciidocAttributes.description = 'Display all Asciidoc attributes that are passed from Gradle'
        asciidocAttributes.group = 'Documentation'
        asciidocAttributes.doLast {
            println 'Current Asciidoctor Attributes'
            println '=============================='
            asciidoc.attributes.each { Object k, Object v ->
                println "${k.toString()}: ${v.toString()}"
            }
        }

        asciidoc.with {
            dependsOn project.tasks.getByPath('gradleRunner')
            sourceDir 'contents'
            outputDir { project.buildDir }
            backends 'html5'
            inputs.dir('samples').withPropertyName('samplesDir').withPathSensitivity(PathSensitivity.RELATIVE)

            attributes "source-highlighter": "coderay",
                    "coderay-linenums-mode": "table",
                    imagesdir            : 'images',
                    stylesheet           : null,
                    linkcss              : true,
                    docinfodir           : '.',
                    docinfo1             : '',
                    nofooter             : true,
                    icons                : 'font',
                    sectanchors          : true,
                    sectlinks            : true,
                    linkattrs            : true,
                    encoding             : 'utf-8',
                    idprefix             : '',
                    toc                  : 'preamble',
                    toclevels            : 1,
                    'toc-title'          : 'Contents',
                    guides               : 'https://guides.gradle.org',
                    'gradle-version'     : gradleVersion,
                    'user-manual-name'   : 'User Manual',
                    'user-manual'        : "https://docs.gradle.org/${gradleVersion}/userguide/",
                    'language-reference' : "https://docs.gradle.org/${gradleVersion}/dsl/",
                    'api-reference'      : "https://docs.gradle.org/${gradleVersion}/javadoc/",
                    'projdir'            : project.projectDir,
                    'codedir'            : project.file('src/main'),
                    'testdir'            : project.file('src/test'),
                    'samplescodedir'     : project.file('samples/code'),
                    'samplesoutputdir'   : project.file('samples/output')

        }

        addAsciidocExtensions(project, asciidoc)
        lazyConfigureMoreAsciidoc(asciidoc)
    }

    @CompileDynamic
    private void addAsciidocExtensions(Project project, AsciidoctorTask asciidoc) {

        GuidesExtension guide = (GuidesExtension)(asciidoc.project.extensions.getByName(org.gradle.guides.BasePlugin.GUIDE_EXTENSION_NAME))

        Closure contribute = { GuidesExtension guides, document, reader, target, attributes ->
            reader.push_include(guides.getContributeMessage(), target, target, 1, attributes)
        }

        asciidoc.extensions {

            includeprocessor(filter: { it == 'contribute' },contribute.curry(guide))

            postprocessor { document, output ->
                if (document.basebackend("html")) {
                    String curOut = output

                    // Inject common styles/meta tags/analytics to head
                    curOut = curOut.replaceAll( ~/<head>/, "<head>${COMMON_HEAD_HTML}")

                    // Inject common header before page title
                    curOut = curOut.replaceAll( ~/<div id="header">/, """${COMMON_HEADER_HTML}<div id="header">""")

                    // Inject common footer at end of content
                    curOut = curOut.replaceAll( ~/<\/body>/, """${COMMON_FOOTER_HTML}</body>""")

                    return curOut
                } else {
                    return output
                }
            }
        }
    }

    @CompileDynamic
    private void lazyConfigureMoreAsciidoc(AsciidoctorTask asciidoc) {

        GuidesExtension guide = (GuidesExtension)(asciidoc.project.extensions.getByName(org.gradle.guides.BasePlugin.GUIDE_EXTENSION_NAME))

        asciidoc.configure {
            sources {
                include 'index.adoc'
            }
        }

        asciidoc.project.afterEvaluate {
            asciidoc.attributes 'repo-path' : guide.repoPath
            asciidoc.attributes authors : guide.getAllAuthors().join(', ')
        }
    }

    private void addGithubPages(Project project) {
        project.apply plugin : 'org.ajoberstar.git-publish'

        GitPublishExtension githubPages = (GitPublishExtension)(project.extensions.getByName('gitPublish'))
        GuidesExtension guide = (GuidesExtension)(project.extensions.getByName(org.gradle.guides.BasePlugin.GUIDE_EXTENSION_NAME))
        AsciidoctorTask asciidoc = (AsciidoctorTask)(project.tasks.getByName('asciidoctor'))
        String ghToken = System.getenv("GRGIT_USER")

        githubPages.branch = 'gh-pages'
        githubPages.commitMessage = "Publish to GitHub Pages"
        githubPages.contents.from {"${asciidoc.outputDir}/${AsciidoctorBackend.HTML5.id}"}
        
        project.afterEvaluate {
            if (ghToken) {
                githubPages.repoUri = "https://github.com/${guide.repoPath}.git"
            } else {
                githubPages.repoUri = "git@github.com:${guide.repoPath}.git"
            }
        }
    }

    @CompileDynamic
    private void addCloudCI(Project project) {
        project.apply plugin : 'org.ysb33r.cloudci'

        project.travisci  {

            check.dependsOn CHECK_LINKS_TASK

            gitPublishPush {
                enabled = System.getenv('TRAVIS_BRANCH') == 'master' && System.getenv('TRAVIS_PULL_REQUEST') == 'false' && System.getenv('TRAVIS_OS_NAME') == 'linux'
            }

            if(System.getenv('TRAVIS_OS_NAME') != 'linux') {
                project.tasks.each { t ->
                    if( t.name.startsWith('gitPublish')) {
                        t.enabled = false
                    }
                }
            }
        }
    }

    // TODO: consider shared-head.html file instead. See http://asciidoctor.org/docs/user-manual/#naming-docinfo-files
    private static final String COMMON_HEAD_HTML = """
<link crossorigin href="//assets.gradle.com" rel="preconnect">
<link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i">
<link rel="stylesheet" href="https://guides.gradle.org/css/asciidoctor.css">
<link rel="stylesheet" href="https://guides.gradle.org/css/docs-blue-theme.css">
<link rel="apple-touch-icon" sizes="180x180" href="https://guides.gradle.org/icon/apple-touch-icon.png">
<link rel="icon" type="image/png" href="https://guides.gradle.org/icon/favicon-32x32.png" sizes="32x32">
<link rel="icon" type="image/png" href="https://guides.gradle.org/icon/favicon-16x16.png" sizes="16x16">
<link rel="manifest" href="https://guides.gradle.org/icon/manifest.json">
<link rel="mask-icon" href="https://guides.gradle.org/icon/safari-pinned-tab.svg" color="#5bbad5">
<link rel="shortcut icon" href="https://guides.gradle.org/icon/favicon.ico">
<script defer src="https://guides.gradle.org/js/set-time-to-complete-text.js"></script>
<script defer src="https://guides.gradle.org/js/analytics.js"></script>
"""

    // TODO: Figure out a way to externalize this.
    private static final String COMMON_HEADER_HTML = """
<header class="site-layout__header site-header" itemscope="itemscope" itemtype="https://schema.org/WPHeader">
    <nav class="site-header__navigation" itemscope="itemscope" itemtype="https://schema.org/SiteNavigationElement">
        <div class="site-header__navigation-header">
            <a target="_top" class="logo" href="https://guides.gradle.org" title="Gradle Docs"><svg width="179px" height="36px" viewBox="0 0 214 43" version="1.1" xmlns="http://www.w3.org/2000/svg"><g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd"><path d="M46.8764467,7.88772959 C45.3200364,5.08928963 42.538267,4.25293158 40.5730047,4.21328173 C38.1617855,4.16464717 36.1814618,5.49994885 36.5560017,6.37654271 C36.6363956,6.56473304 37.0884369,7.5470924 37.3659256,7.95257566 C37.767596,8.5393154 38.4866528,8.08851802 38.7385072,7.95462652 C39.4905793,7.55490518 40.2943191,7.42550602 41.2059822,7.53175982 C42.0774483,7.63332596 43.2351806,8.17045455 44.0118896,9.6627931 C45.8397043,13.1750281 40.1972679,20.4036071 33.1344723,15.3978616 C26.0715771,10.3921161 19.203283,12.0507692 16.0936541,13.059692 C12.9841249,14.0684195 11.5547888,15.0829089 12.7835391,17.4289889 C14.4533587,20.6171889 13.8998775,19.6389313 15.519027,22.2780882 C18.0908346,26.4697421 23.7203042,20.3491129 23.7203042,20.3491129 C19.526654,26.5308771 15.931769,25.0372689 14.5529036,22.8762541 C13.3102888,20.9285282 12.3447638,18.6830377 12.3447638,18.6830377 C1.72319071,22.4290701 4.5969243,38.9663805 4.5969243,38.9663805 L9.87170315,38.9663805 C11.2165558,32.8772955 16.0248305,33.1018153 16.848918,38.9663805 L20.8738008,38.9663805 C24.4333764,27.0763067 33.4552501,38.9663805 33.4552501,38.9663805 L38.701901,38.9663805 C37.2324677,30.8555435 41.6532357,28.3073078 44.4391943,23.5530361 C47.2254521,18.7983738 49.8646868,12.9818572 46.8764467,7.88772959 L46.8764467,7.88772959 Z M33.3433369,23.5472742 C30.5694474,22.6419684 31.5616042,19.8816157 31.5616042,19.8816157 C31.5616042,19.8816157 33.9840944,20.6649445 37.2609946,21.7341234 C37.0723781,22.5903064 35.4423564,24.232455 33.3433369,23.5472742 L33.3433369,23.5472742 Z" id="Fill-9" fill="#02303A"></path><path d="M35.8042288,21.4659498 C35.8871163,22.2300396 35.3025149,22.9172712 34.4983762,23.0008679 C33.6942375,23.0844647 32.9752804,22.5327848 32.8923929,21.7685973 C32.8095053,21.0045075 33.3942065,20.3172758 34.1982455,20.2336791 C35.0023842,20.1500824 35.721441,20.7017623 35.8042288,21.4659498" id="Fill-10" fill="#00303A"></path><path d="M72.236,22.3 L72.236,29.32 C71.3559956,29.9680032 70.418005,30.4419985 69.422,30.742 C68.425995,31.0420015 67.3600057,31.192 66.224,31.192 C64.8079929,31.192 63.5260057,30.9720022 62.378,30.532 C61.2299943,30.0919978 60.2500041,29.4800039 59.438,28.696 C58.6259959,27.9119961 58.0000022,26.9760054 57.56,25.888 C57.1199978,24.7999946 56.9,23.6120064 56.9,22.324 C56.9,21.0199935 57.1119979,19.8240054 57.536,18.736 C57.9600021,17.6479946 58.5619961,16.7120039 59.342,15.928 C60.1220039,15.1439961 61.0679944,14.5360022 62.18,14.104 C63.2920056,13.6719978 64.5399931,13.456 65.924,13.456 C66.6280035,13.456 67.2859969,13.5119994 67.898,13.624 C68.5100031,13.7360006 69.0779974,13.889999 69.602,14.086 C70.1260026,14.282001 70.6039978,14.5199986 71.036,14.8 C71.4680022,15.0800014 71.8639982,15.3879983 72.224,15.724 L71.3,17.188 C71.1559993,17.4200012 70.9680012,17.5619997 70.736,17.614 C70.5039988,17.6660003 70.2520014,17.6080008 69.98,17.44 L69.188,16.984 C68.9239987,16.8319992 68.6300016,16.7000006 68.306,16.588 C67.9819984,16.4759994 67.618002,16.3840004 67.214,16.312 C66.809998,16.2399996 66.3440026,16.204 65.816,16.204 C64.9599957,16.204 64.1860035,16.3479986 63.494,16.636 C62.8019965,16.9240014 62.2120024,17.3359973 61.724,17.872 C61.2359976,18.4080027 60.8600013,19.0519962 60.596,19.804 C60.3319987,20.5560038 60.2,21.3959954 60.2,22.324 C60.2,23.316005 60.3419986,24.2019961 60.626,24.982 C60.9100014,25.7620039 61.3099974,26.4219973 61.826,26.962 C62.3420026,27.5020027 62.9639964,27.9139986 63.692,28.198 C64.4200036,28.4820014 65.2319955,28.624 66.128,28.624 C66.7680032,28.624 67.3399975,28.5560007 67.844,28.42 C68.3480025,28.2839993 68.8399976,28.1000012 69.32,27.868 L69.32,24.724 L67.136,24.724 C66.927999,24.724 66.7660006,24.6660006 66.65,24.55 C66.5339994,24.4339994 66.476,24.2920008 66.476,24.124 L66.476,22.3 L72.236,22.3 Z M77.504,20.824 C77.8880019,20.0879963 78.3439974,19.5100021 78.872,19.09 C79.4000026,18.6699979 80.0239964,18.46 80.744,18.46 C81.3120028,18.46 81.7679983,18.5839988 82.112,18.832 L81.92,21.052 C81.8799998,21.1960007 81.8220004,21.2979997 81.746,21.358 C81.6699996,21.4180003 81.5680006,21.448 81.44,21.448 C81.3199994,21.448 81.1420012,21.4280002 80.906,21.388 C80.6699988,21.3479998 80.4400011,21.328 80.216,21.328 C79.8879984,21.328 79.5960013,21.3759995 79.34,21.472 C79.0839987,21.5680005 78.854001,21.7059991 78.65,21.886 C78.445999,22.0660009 78.2660008,22.2839987 78.11,22.54 C77.9539992,22.7960013 77.8080007,23.0879984 77.672,23.416 L77.672,31 L74.708,31 L74.708,18.688 L76.448,18.688 C76.7520015,18.688 76.9639994,18.7419995 77.084,18.85 C77.2040006,18.9580005 77.2839998,19.1519986 77.324,19.432 L77.504,20.824 Z M90.008,25.744 C89.1519957,25.7840002 88.4320029,25.8579995 87.848,25.966 C87.2639971,26.0740005 86.7960018,26.2119992 86.444,26.38 C86.0919982,26.5480008 85.8400008,26.7439989 85.688,26.968 C85.5359992,27.1920011 85.46,27.4359987 85.46,27.7 C85.46,28.2200026 85.6139985,28.5919989 85.922,28.816 C86.2300015,29.0400011 86.6319975,29.152 87.128,29.152 C87.736003,29.152 88.2619978,29.0420011 88.706,28.822 C89.1500022,28.6019989 89.5839979,28.2680022 90.008,27.82 L90.008,25.744 Z M83.216,20.404 C84.6320071,19.1079935 86.33599,18.46 88.328,18.46 C89.0480036,18.46 89.6919972,18.5779988 90.26,18.814 C90.8280028,19.0500012 91.307998,19.3779979 91.7,19.798 C92.092002,20.2180021 92.389999,20.7199971 92.594,21.304 C92.798001,21.8880029 92.9,22.5279965 92.9,23.224 L92.9,31 L91.556,31 C91.2759986,31 91.0600008,30.9580004 90.908,30.874 C90.7559992,30.7899996 90.6360004,30.6200013 90.548,30.364 L90.284,29.476 C89.9719984,29.7560014 89.6680015,30.0019989 89.372,30.214 C89.0759985,30.4260011 88.7680016,30.6039993 88.448,30.748 C88.1279984,30.8920007 87.7860018,31.0019996 87.422,31.078 C87.0579982,31.1540004 86.6560022,31.192 86.216,31.192 C85.6959974,31.192 85.2160022,31.1220007 84.776,30.982 C84.3359978,30.8419993 83.9560016,30.6320014 83.636,30.352 C83.3159984,30.0719986 83.0680009,29.7240021 82.892,29.308 C82.7159991,28.8919979 82.628,28.4080028 82.628,27.856 C82.628,27.5439984 82.6799995,27.2340015 82.784,26.926 C82.8880005,26.6179985 83.0579988,26.3240014 83.294,26.044 C83.5300012,25.7639986 83.8359981,25.5000012 84.212,25.252 C84.5880019,25.0039988 85.0499973,24.7880009 85.598,24.604 C86.1460027,24.4199991 86.7839964,24.2700006 87.512,24.154 C88.2400036,24.0379994 89.0719953,23.9680001 90.008,23.944 L90.008,23.224 C90.008,22.3999959 89.8320018,21.790002 89.48,21.394 C89.1279982,20.997998 88.6200033,20.8 87.956,20.8 C87.4759976,20.8 87.0780016,20.8559994 86.762,20.968 C86.4459984,21.0800006 86.1680012,21.2059993 85.928,21.346 C85.6879988,21.4860007 85.470001,21.6119994 85.274,21.724 C85.077999,21.8360006 84.8600012,21.892 84.62,21.892 C84.411999,21.892 84.2360007,21.8380005 84.092,21.73 C83.9479993,21.6219995 83.8320004,21.4960007 83.744,21.352 L83.216,20.404 Z M103.412,21.832 C103.075998,21.423998 102.710002,21.1360008 102.314,20.968 C101.917998,20.7999992 101.492002,20.716 101.036,20.716 C100.587998,20.716 100.184002,20.7999992 99.824,20.968 C99.4639982,21.1360008 99.1560013,21.3899983 98.9,21.73 C98.6439987,22.0700017 98.4480007,22.5019974 98.312,23.026 C98.1759993,23.5500026 98.108,24.1679964 98.108,24.88 C98.108,25.6000036 98.1659994,26.2099975 98.282,26.71 C98.3980006,27.2100025 98.5639989,27.6179984 98.78,27.934 C98.9960011,28.2500016 99.2599984,28.4779993 99.572,28.618 C99.8840016,28.7580007 100.231998,28.828 100.616,28.828 C101.232003,28.828 101.755998,28.7000013 102.188,28.444 C102.620002,28.1879987 103.027998,27.8240024 103.412,27.352 L103.412,21.832 Z M106.376,13.168 L106.376,31 L104.564,31 C104.171998,31 103.924001,30.8200018 103.82,30.46 L103.568,29.272 C103.071998,29.8400028 102.502003,30.2999982 101.858,30.652 C101.213997,31.0040018 100.464004,31.18 99.608,31.18 C98.9359966,31.18 98.3200028,31.0400014 97.76,30.76 C97.1999972,30.4799986 96.718002,30.0740027 96.314,29.542 C95.909998,29.0099973 95.5980011,28.3520039 95.378,27.568 C95.1579989,26.7839961 95.048,25.888005 95.048,24.88 C95.048,23.9679954 95.1719988,23.1200039 95.42,22.336 C95.6680012,21.5519961 96.0239977,20.8720029 96.488,20.296 C96.9520023,19.7199971 97.5079968,19.2700016 98.156,18.946 C98.8040032,18.6219984 99.531996,18.46 100.34,18.46 C101.028003,18.46 101.615998,18.5679989 102.104,18.784 C102.592002,19.0000011 103.027998,19.2919982 103.412,19.66 L103.412,13.168 L106.376,13.168 Z M112.304,13.168 L112.304,31 L109.34,31 L109.34,13.168 L112.304,13.168 Z M123.2,23.428 C123.2,23.0439981 123.146001,22.6820017 123.038,22.342 C122.929999,22.0019983 122.768001,21.7040013 122.552,21.448 C122.335999,21.1919987 122.062002,20.9900007 121.73,20.842 C121.397998,20.6939993 121.012002,20.62 120.572,20.62 C119.715996,20.62 119.042002,20.8639976 118.55,21.352 C118.057998,21.8400024 117.744001,22.5319955 117.608,23.428 L123.2,23.428 Z M117.548,25.216 C117.596,25.8480032 117.707999,26.3939977 117.884,26.854 C118.060001,27.3140023 118.291999,27.6939985 118.58,27.994 C118.868001,28.2940015 119.209998,28.5179993 119.606,28.666 C120.002002,28.8140007 120.439998,28.888 120.92,28.888 C121.400002,28.888 121.813998,28.8320006 122.162,28.72 C122.510002,28.6079994 122.813999,28.4840007 123.074,28.348 C123.334001,28.2119993 123.561999,28.0880006 123.758,27.976 C123.954001,27.8639994 124.143999,27.808 124.328,27.808 C124.576001,27.808 124.759999,27.8999991 124.88,28.084 L125.732,29.164 C125.403998,29.5480019 125.036002,29.8699987 124.628,30.13 C124.219998,30.3900013 123.794002,30.5979992 123.35,30.754 C122.905998,30.9100008 122.454002,31.0199997 121.994,31.084 C121.533998,31.1480003 121.088002,31.18 120.656,31.18 C119.799996,31.18 119.004004,31.0380014 118.268,30.754 C117.531996,30.4699986 116.892003,30.0500028 116.348,29.494 C115.803997,28.9379972 115.376002,28.2500041 115.064,27.43 C114.751998,26.6099959 114.596,25.6600054 114.596,24.58 C114.596,23.7399958 114.731999,22.9500037 115.004,22.21 C115.276001,21.4699963 115.665997,20.8260027 116.174,20.278 C116.682003,19.7299973 117.301996,19.2960016 118.034,18.976 C118.766004,18.6559984 119.591995,18.496 120.512,18.496 C121.288004,18.496 122.003997,18.6199988 122.66,18.868 C123.316003,19.1160012 123.879998,19.4779976 124.352,19.954 C124.824002,20.4300024 125.193999,21.0139965 125.462,21.706 C125.730001,22.3980035 125.864,23.1879956 125.864,24.076 C125.864,24.5240022 125.816,24.8259992 125.72,24.982 C125.624,25.1380008 125.440001,25.216 125.168,25.216 L117.548,25.216 Z M148.832,23.296 L148.832,29.332 C147.983996,29.932003 147.080005,30.3919984 146.12,30.712 C145.159995,31.0320016 144.080006,31.192 142.88,31.192 C141.535993,31.192 140.328005,30.9860021 139.256,30.574 C138.183995,30.1619979 137.270004,29.5780038 136.514,28.822 C135.757996,28.0659962 135.178002,27.1520054 134.774,26.08 C134.369998,25.0079946 134.168,23.8160066 134.168,22.504 C134.168,21.1919934 134.365998,20.0020053 134.762,18.934 C135.158002,17.8659947 135.725996,16.9540038 136.466,16.198 C137.206004,15.4419962 138.099995,14.8580021 139.148,14.446 C140.196005,14.0339979 141.375993,13.828 142.688,13.828 C143.336003,13.828 143.933997,13.8719996 144.482,13.96 C145.030003,14.0480004 145.539998,14.1779991 146.012,14.35 C146.484002,14.5220009 146.923998,14.7339987 147.332,14.986 C147.740002,15.2380013 148.131998,15.5279984 148.508,15.856 L148.172,16.384 C148.059999,16.576001 147.892001,16.6200005 147.668,16.516 C147.547999,16.4679998 147.376001,16.3560009 147.152,16.18 C146.927999,16.0039991 146.620002,15.820001 146.228,15.628 C145.835998,15.435999 145.350003,15.2640008 144.77,15.112 C144.189997,14.9599992 143.484004,14.884 142.652,14.884 C141.547994,14.884 140.550004,15.0599982 139.658,15.412 C138.765996,15.7640018 138.006003,16.2679967 137.378,16.924 C136.749997,17.5800033 136.266002,18.3799953 135.926,19.324 C135.585998,20.2680047 135.416,21.3279941 135.416,22.504 C135.416,23.696006 135.585998,24.7679952 135.926,25.72 C136.266002,26.6720048 136.757997,27.4799967 137.402,28.144 C138.046003,28.8080033 138.827995,29.3159982 139.748,29.668 C140.668005,30.0200018 141.703994,30.196 142.856,30.196 C143.360003,30.196 143.825998,30.1640003 144.254,30.1 C144.682002,30.0359997 145.089998,29.9460006 145.478,29.83 C145.866002,29.7139994 146.241998,29.5700009 146.606,29.398 C146.970002,29.2259991 147.339998,29.0320011 147.716,28.816 L147.716,24.244 L144.464,24.244 C144.376,24.244 144.302,24.2160003 144.242,24.16 C144.182,24.1039997 144.152,24.0400004 144.152,23.968 L144.152,23.296 L148.832,23.296 Z M161.408,19.06 L161.408,31 L160.772,31 C160.571999,31 160.456,30.896001 160.424,30.688 L160.316,28.912 C159.763997,29.6000034 159.126004,30.1519979 158.402,30.568 C157.677996,30.9840021 156.872004,31.192 155.984,31.192 C155.319997,31.192 154.740002,31.088001 154.244,30.88 C153.747998,30.671999 153.336002,30.372002 153.008,29.98 C152.679998,29.587998 152.432001,29.1160028 152.264,28.564 C152.095999,28.0119972 152.012,27.3880035 152.012,26.692 L152.012,19.06 L153.164,19.06 L153.164,26.692 C153.164,27.8120056 153.419997,28.6899968 153.932,29.326 C154.444003,29.9620032 155.223995,30.28 156.272,30.28 C157.048004,30.28 157.773997,30.082002 158.45,29.686 C159.126003,29.289998 159.727997,28.7400035 160.256,28.036 L160.256,19.06 L161.408,19.06 Z M166.604,19.06 L166.604,31 L165.464,31 L165.464,19.06 L166.604,19.06 Z M167.096,14.944 C167.096,15.0880007 167.066,15.2219994 167.006,15.346 C166.946,15.4700006 166.868,15.5799995 166.772,15.676 C166.676,15.7720005 166.564001,15.8479997 166.436,15.904 C166.307999,15.9600003 166.172001,15.988 166.028,15.988 C165.883999,15.988 165.748001,15.9600003 165.62,15.904 C165.491999,15.8479997 165.38,15.7720005 165.284,15.676 C165.188,15.5799995 165.112,15.4700006 165.056,15.346 C165,15.2219994 164.972,15.0880007 164.972,14.944 C164.972,14.7999993 165,14.6620007 165.056,14.53 C165.112,14.3979993 165.188,14.2840005 165.284,14.188 C165.38,14.0919995 165.491999,14.0160003 165.62,13.96 C165.748001,13.9039997 165.883999,13.876 166.028,13.876 C166.172001,13.876 166.307999,13.9039997 166.436,13.96 C166.564001,14.0160003 166.676,14.0919995 166.772,14.188 C166.868,14.2840005 166.946,14.3979993 167.006,14.53 C167.066,14.6620007 167.096,14.7999993 167.096,14.944 Z M178.82,21.628 C178.363998,20.9399966 177.858003,20.4560014 177.302,20.176 C176.745997,19.8959986 176.112004,19.756 175.4,19.756 C174.695996,19.756 174.080003,19.8839987 173.552,20.14 C173.023997,20.3960013 172.582002,20.7579977 172.226,21.226 C171.869998,21.6940023 171.602001,22.2539967 171.422,22.906 C171.241999,23.5580033 171.152,24.2759961 171.152,25.06 C171.152,26.8360089 171.477997,28.1419958 172.13,28.978 C172.782003,29.8140042 173.723994,30.232 174.956,30.232 C175.740004,30.232 176.455997,30.028002 177.104,29.62 C177.752003,29.211998 178.323998,28.6400037 178.82,27.904 L178.82,21.628 Z M179.96,13.54 L179.96,31 L179.336,31 C179.127999,31 179.008,30.896001 178.976,30.688 L178.856,28.804 C178.327997,29.5240036 177.712004,30.0959979 177.008,30.52 C176.303996,30.9440021 175.512004,31.156 174.632,31.156 C173.159993,31.156 172.016004,30.6480051 171.2,29.632 C170.383996,28.6159949 169.976,27.0920102 169.976,25.06 C169.976,24.1879956 170.089999,23.3740038 170.318,22.618 C170.546001,21.8619962 170.879998,21.2060028 171.32,20.65 C171.760002,20.0939972 172.301997,19.6560016 172.946,19.336 C173.590003,19.0159984 174.331996,18.856 175.172,18.856 C175.980004,18.856 176.679997,19.0039985 177.272,19.3 C177.864003,19.5960015 178.379998,20.0359971 178.82,20.62 L178.82,13.54 L179.96,13.54 Z M192.272,23.824 C192.272,23.1839968 192.182001,22.6100025 192.002,22.102 C191.821999,21.5939975 191.568002,21.1640018 191.24,20.812 C190.911998,20.4599982 190.522002,20.1900009 190.07,20.002 C189.617998,19.8139991 189.120003,19.72 188.576,19.72 C187.959997,19.72 187.408002,19.815999 186.92,20.008 C186.431998,20.200001 186.010002,20.4759982 185.654,20.836 C185.297998,21.1960018 185.012001,21.6279975 184.796,22.132 C184.579999,22.6360025 184.436,23.1999969 184.364,23.824 L192.272,23.824 Z M184.304,24.556 L184.304,24.784 C184.304,25.6800045 184.407999,26.4679966 184.616,27.148 C184.824001,27.8280034 185.119998,28.3979977 185.504,28.858 C185.888002,29.3180023 186.351997,29.6639988 186.896,29.896 C187.440003,30.1280012 188.047997,30.244 188.72,30.244 C189.320003,30.244 189.839998,30.1780007 190.28,30.046 C190.720002,29.9139993 191.089999,29.7660008 191.39,29.602 C191.690002,29.4379992 191.927999,29.2900007 192.104,29.158 C192.280001,29.0259993 192.408,28.96 192.488,28.96 C192.592001,28.96 192.672,28.9999996 192.728,29.08 L193.04,29.464 C192.847999,29.7040012 192.594002,29.927999 192.278,30.136 C191.961998,30.344001 191.610002,30.5219993 191.222,30.67 C190.833998,30.8180007 190.418002,30.9359996 189.974,31.024 C189.529998,31.1120004 189.084002,31.156 188.636,31.156 C187.819996,31.156 187.076003,31.0140014 186.404,30.73 C185.731997,30.4459986 185.156002,30.0320027 184.676,29.488 C184.195998,28.9439973 183.826001,28.2780039 183.566,27.49 C183.305999,26.7019961 183.176,25.8000051 183.176,24.784 C183.176,23.9279957 183.297999,23.1380036 183.542,22.414 C183.786001,21.6899964 184.137998,21.0660026 184.598,20.542 C185.058002,20.0179974 185.621997,19.6080015 186.29,19.312 C186.958003,19.0159985 187.715996,18.868 188.564,18.868 C189.236003,18.868 189.859997,18.9839988 190.436,19.216 C191.012003,19.4480012 191.511998,19.7859978 191.936,20.23 C192.360002,20.6740022 192.693999,21.2199968 192.938,21.868 C193.182001,22.5160032 193.304,23.2599958 193.304,24.1 C193.304,24.2760009 193.28,24.3959997 193.232,24.46 C193.184,24.5240003 193.104001,24.556 192.992,24.556 L184.304,24.556 Z M202.976,20.536 C202.912,20.6480006 202.820001,20.704 202.7,20.704 C202.612,20.704 202.500001,20.6540005 202.364,20.554 C202.227999,20.4539995 202.046001,20.3420006 201.818,20.218 C201.589999,20.0939994 201.308002,19.9820005 200.972,19.882 C200.635998,19.7819995 200.228002,19.732 199.748,19.732 C199.315998,19.732 198.922002,19.7939994 198.566,19.918 C198.209998,20.0420006 197.906001,20.207999 197.654,20.416 C197.401999,20.624001 197.208001,20.8659986 197.072,21.142 C196.935999,21.4180014 196.868,21.7079985 196.868,22.012 C196.868,22.3880019 196.963999,22.6999988 197.156,22.948 C197.348001,23.1960012 197.597998,23.4079991 197.906,23.584 C198.214002,23.7600009 198.567998,23.9119994 198.968,24.04 C199.368002,24.1680006 199.773998,24.2959994 200.186,24.424 C200.598002,24.5520006 201.003998,24.6939992 201.404,24.85 C201.804002,25.0060008 202.157998,25.1999988 202.466,25.432 C202.774002,25.6640012 203.023999,25.9479983 203.216,26.284 C203.408001,26.6200017 203.504,27.0279976 203.504,27.508 C203.504,28.0280026 203.412001,28.5119978 203.228,28.96 C203.043999,29.4080022 202.774002,29.7959984 202.418,30.124 C202.061998,30.4520016 201.624003,30.711999 201.104,30.904 C200.583997,31.096001 199.988003,31.192 199.316,31.192 C198.475996,31.192 197.752003,31.0580013 197.144,30.79 C196.535997,30.5219987 195.992002,30.1720022 195.512,29.74 L195.776,29.332 C195.816,29.2679997 195.862,29.2200002 195.914,29.188 C195.966,29.1559998 196.036,29.14 196.124,29.14 C196.228001,29.14 196.353999,29.2039994 196.502,29.332 C196.650001,29.4600006 196.847999,29.5979993 197.096,29.746 C197.344001,29.8940007 197.649998,30.0319994 198.014,30.16 C198.378002,30.2880006 198.827997,30.352 199.364,30.352 C199.868003,30.352 200.311998,30.2820007 200.696,30.142 C201.080002,30.0019993 201.399999,29.8120012 201.656,29.572 C201.912001,29.3319988 202.105999,29.0500016 202.238,28.726 C202.370001,28.4019984 202.436,28.0600018 202.436,27.7 C202.436,27.299998 202.340001,26.9680013 202.148,26.704 C201.955999,26.4399987 201.704002,26.2160009 201.392,26.032 C201.079998,25.8479991 200.726002,25.6920006 200.33,25.564 C199.933998,25.4359994 199.528002,25.3080006 199.112,25.18 C198.695998,25.0519994 198.290002,24.9120008 197.894,24.76 C197.497998,24.6079992 197.144002,24.4160012 196.832,24.184 C196.519998,23.9519988 196.268001,23.6700017 196.076,23.338 C195.883999,23.0059983 195.788,22.5920025 195.788,22.096 C195.788,21.6719979 195.879999,21.264002 196.064,20.872 C196.248001,20.479998 196.509998,20.1360015 196.85,19.84 C197.190002,19.5439985 197.603998,19.3080009 198.092,19.132 C198.580002,18.9559991 199.127997,18.868 199.736,18.868 C200.464004,18.868 201.109997,18.971999 201.674,19.18 C202.238003,19.388001 202.751998,19.7079978 203.216,20.14 L202.976,20.536 Z" fill="#02303A"></path></g></svg></a>
            <button type="button" aria-label="Navigation Menu" class="site-header__navigation-button hamburger">
                <span class="hamburger__bar"></span>
                <span class="hamburger__bar"></span>
                <span class="hamburger__bar"></span>
            </button>
        </div>
        <div class="site-header__navigation-collapsible site-header__navigation-collapsible--collapse">
            <ul class="site-header__navigation-items">
                <li class="site-header__navigation-item site-header__navigation-submenu-section" tabindex="0">
                            <span class="site-header__navigation-link">
                                Docs
                                <svg class="site-header__down-arrow site-header__icon-light" width="19" height="11" viewBox="0 0 19 11" xmlns="http://www.w3.org/2000/svg"><title>Open Docs Menu</title><path transform="rotate(-180 9.374 5.494)" d="M17.9991 10.422825L9.3741 0.565575 0.7491 10.422825" stroke="#02303A" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                    <div class="site-header__navigation-submenu">
                        <div class="site-header__navigation-submenu-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-submenu-item-link" href="https://docs.gradle.org/current/userguide/userguide.html" itemprop="url">
                                <span class="site-header__navigation-submenu-item-link-text">User Manual</span>
                            </a>
                        </div>
                        <div class="site-header__navigation-submenu-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-submenu-item-link" href="https://guides.gradle.org" itemprop="url">
                                <span class="site-header__navigation-submenu-item-link-text">Guides and Tutorials</span>
                            </a>
                        </div>
                        <div class="site-header__navigation-submenu-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-submenu-item-link" href="https://docs.gradle.org/current/dsl/" itemprop="url">
                                <span class="site-header__navigation-submenu-item-link-text">DSL Reference</span>
                            </a>
                        </div>
                        <div class="site-header__navigation-submenu-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-submenu-item-link" href="https://docs.gradle.org/current/javadoc/" itemprop="url">
                                <span class="site-header__navigation-submenu-item-link-text">Javadoc</span>
                            </a>
                        </div>
                        <div class="site-header__navigation-submenu-item" itemprop="name">
                            <a target="_top" class="site-header__navigation-submenu-item-link" href="https://docs.gradle.org/current/release-notes.html" itemprop="url">
                                <span class="site-header__navigation-submenu-item-link-text">Release Notes</span>
                            </a>
                        </div>
                    </div>
                </li>
                <li class="site-header__navigation-item" itemprop="name">
                    <a target="_top" class="site-header__navigation-link" href="https://discuss.gradle.org/" itemprop="url">Forums</a>
                </li>
                <li class="site-header__navigation-item" itemprop="name">
                    <a target="_top" class="site-header__navigation-link" href="https://gradle.org/training/" itemprop="url">Training</a>
                </li>
                <li class="site-header__navigation-item" itemprop="name">
                    <a target="_top" class="site-header__navigation-link" href="https://gradle.com/enterprise" itemprop="url">Enterprise</a>
                </li>
            </ul>
        </div>
    </nav>
</header>
"""

    private static final String COMMON_FOOTER_HTML = """
<footer class="site-layout__footer site-footer" itemscope="itemscope" itemtype="https://schema.org/WPFooter">
    <nav class="site-footer__navigation" itemtype="https://schema.org/SiteNavigationElement">
        <section class="site-footer__links">
            <div class="site-footer__link-group">
                <header><strong>Docs</strong></header>
                <ul class="site-footer__links-list">
                    <li itemprop="name"><a href="https://docs.gradle.org/userguide/userguide.html" itemprop="url">User Manual</a></li>
                    <li itemprop="name"><a href="https://docs.gradle.org/current/dsl/" itemprop="url">DSL Reference</a></li>
                    <li itemprop="name"><a href="https://docs.gradle.org/current/release-notes.html" itemprop="url">Release Notes</a></li>
                    <li itemprop="name"><a href="https://docs.gradle.org/current/javadoc/" itemprop="url">Javadoc</a></li>
                </ul>
            </div>
            <div class="site-footer__link-group">
                <header><strong>News</strong></header>
                <ul class="site-footer__links-list">
                    <li itemprop="name"><a href="https://blog.gradle.org/" itemprop="url">Blog</a></li>
                    <li itemprop="name"><a href="https://newsletter.gradle.com/" itemprop="url">Newsletter</a></li>
                    <li itemprop="name"><a href="https://twitter.com/gradle" itemprop="url">Twitter</a></li>
                </ul>
            </div>
            <div class="site-footer__link-group">
                <header><strong>Products</strong></header>
                <ul class="site-footer__links-list">
                    <li itemprop="name"><a href="https://gradle.com/build-scans" itemprop="url">Build Scans</a></li>
                    <li itemprop="name"><a href="https://gradle.com/build-cache" itemprop="url">Build Cache</a></li>
                    <li itemprop="name"><a href="https://gradle.com/enterprise/resources" itemprop="url">Enterprise Docs</a></li>
                </ul>
            </div>
            <div class="site-footer__link-group">
                <header><strong>Get Help</strong></header>
                <ul class="site-footer__links-list">
                    <li itemprop="name"><a href="https://discuss.gradle.org/c/help-discuss" itemprop="url">Forums</a></li>
                    <li itemprop="name"><a href="https://github.com/gradle/" itemprop="url">GitHub</a></li>
                    <li itemprop="name"><a href="https://gradle.org/training/" itemprop="url">Training</a></li>
                    <li itemprop="name"><a href="https://gradle.org/services/" itemprop="url">Services</a></li>
                </ul>
            </div>
        </section>
        <section class="site-footer__subscribe-newsletter">
            <p>Subscribe for important Gradle updates and news</p>
            <form id="newsletter-form" class="newsletter-form" action="https://go.pardot.com/l/68052/2017-12-01/b8dh7j" method="post">
                <input id="email" class="email" name="email" type="email" placeholder="name@email.com" pattern="[^@\\s]+@[^@\\s]+\\.[^@\\s]+" maxlength="255" required=""/>
                <button id="submit" class="submit" type="submit">Subscribe</button>
            </form>
        </section>
    </nav>
    <div class="site-footer-secondary">
        <div class="site-footer-secondary__contents">
            <div class="site-footer__copy">© <a href="https://gradle.com">Gradle Inc.</a> <time>2018</time> All rights reserved.</div>
            <div class="site-footer__logo"><a href="https://gradle.org"><svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40" class="logo-icon"><g style="fill:none;opacity:0.7"><g fill="#11272E"><path d="M37.7 7.1C36.4 4.8 34 4 32.4 4 30.4 4 28.7 5.1 29 5.9 29.1 6 29.5 6.8 29.7 7.2 30 7.7 30.6 7.3 30.9 7.2 31.5 6.9 32.2 6.7 32.9 6.8 33.6 6.9 34.6 7.4 35.3 8.6 36.8 11.6 32.1 17.8 26.2 13.5 20.3 9.3 14.5 10.7 11.9 11.5 9.3 12.4 8.1 13.3 9.2 15.3 10.6 18 10.1 17.1 11.5 19.4 13.6 22.9 18.3 17.7 18.3 17.7 14.8 23 11.8 21.7 10.6 19.9 9.6 18.2 8.8 16.3 8.8 16.3 -0.1 19.5 2.3 33.6 2.3 33.6L6.7 33.6C7.9 28.4 11.9 28.6 12.6 33.6L15.9 33.6C18.9 23.5 26.4 33.6 26.4 33.6L30.8 33.6C29.6 26.7 33.3 24.5 35.6 20.5 37.9 16.4 40.1 11.5 37.7 7.1L37.7 7.1ZM26.3 20.5C24 19.7 24.9 17.3 24.9 17.3 24.9 17.3 26.9 18 29.6 18.9 29.5 19.6 28.1 21 26.3 20.5L26.3 20.5Z"/><path d="M28.4 18.7C28.5 19.3 28 19.9 27.3 20 26.6 20.1 26 19.6 26 18.9 25.9 18.3 26.4 17.7 27.1 17.6 27.7 17.6 28.3 18 28.4 18.7"/><path d="M37.7 7.1C36.4 4.8 34 4 32.4 4 30.4 4 28.7 5.1 29 5.9 29.1 6 29.5 6.8 29.7 7.2 30 7.7 30.6 7.3 30.9 7.2 31.5 6.9 32.2 6.7 32.9 6.8 33.6 6.9 34.6 7.4 35.3 8.6 36.8 11.6 32.1 17.8 26.2 13.5 20.3 9.3 14.5 10.7 11.9 11.5 9.3 12.4 8.1 13.3 9.2 15.3 10.6 18 10.1 17.1 11.5 19.4 13.6 22.9 18.3 17.7 18.3 17.7 14.8 23 11.8 21.7 10.6 19.9 9.6 18.2 8.8 16.3 8.8 16.3 -0.1 19.5 2.3 33.6 2.3 33.6L6.7 33.6C7.9 28.4 11.9 28.6 12.6 33.6L15.9 33.6C18.9 23.5 26.4 33.6 26.4 33.6L30.8 33.6C29.6 26.7 33.3 24.5 35.6 20.5 37.9 16.4 40.1 11.5 37.7 7.1L37.7 7.1ZM26.3 20.5C24 19.7 24.9 17.3 24.9 17.3 24.9 17.3 26.9 18 29.6 18.9 29.5 19.6 28.1 21 26.3 20.5L26.3 20.5Z"/><path d="M28.4 18.7C28.5 19.3 28 19.9 27.3 20 26.6 20.1 26 19.6 26 18.9 25.9 18.3 26.4 17.7 27.1 17.6 27.7 17.6 28.3 18 28.4 18.7"/></g></g></svg></a></div>
            <div class="site-footer-secondary__links">
                <a href="https://gradle.com/careers" >Careers</a> |
                <a href="https://gradle.org/privacy">Privacy</a> |
                <a href="https://gradle.org/terms">Terms of Service</a> |
                <a href="https://gradle.org/contact/">Contact</a>
            </div>
        </div>
    </div>
</footer>

"""
}
