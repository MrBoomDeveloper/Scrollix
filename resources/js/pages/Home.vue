<script setup lang="ts">
import AppWrapper from '@/layouts/AppWrapper.vue';
import { Head, Link } from '@inertiajs/vue3';
import { onMounted, onUnmounted, Ref, ref } from 'vue';
import { formatDistance, subDays } from "date-fns";

interface Props {
    tab: "accepted" | "not_decided" | "not_accepted"
}

enum NsfwMode {
    BLUR, UNBLUR, HIDE
}

withDefaults(defineProps<Props>(), {
    tab: "accepted"
});

const loadMoreTrigger = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | null = null;
const afterCursor = ref<string | null>(null);
const didReachEnd = ref(false);
const isLoading = ref(false);
const nsfwMode = ref(NsfwMode.BLUR);
const posts: Ref<RedditPost[]> = ref([]);

interface RedditPost {
    author: string,
    title: string,
    selftext: string,
    score: number,
    created: number,
    permalink: string,
    over_18: boolean,
    num_comments: number,
    preview: {
        images: RedditPostImageWrapper[]
    }
}

interface RedditPostImageWrapper {
    source: RedditPostImage,
    resolutions: RedditPostImage[]
}

interface RedditPostImage {
    url: string,
    width: number,
    height: number
}

async function fetchRedditPosts() {
    if(isLoading.value || didReachEnd.value) return
    isLoading.value = true;

    try {
        let url = `https://www.reddit.com/r/AnimeART/top.json?limit=10&t=all`;
        if(afterCursor.value) url += `&after=${afterCursor.value}`

        const response = await (await fetch(url)).json();
        
        for(const post of response.data.children) {
            posts.value.push(post.data);
        }

        if(response.data.after == null) {
            console.info("Reached to the end of the feed!");
            didReachEnd.value = true;
        }

        afterCursor.value = response.data.after;
    } catch(e) {
        console.error(e);
        alert("Failed to fetch Reddit posts!");
    } finally {
        isLoading.value = false;
    }
}

onMounted(() => {
    fetchRedditPosts();

    observer = new IntersectionObserver((entries) => {
        const target = entries[0];
        if(target.isIntersecting) {
            fetchRedditPosts();
        }
    }, {
        root: null,
        rootMargin: '200px', // Trigger loading 200px before reaching the absolute bottom
        threshold: 0
    });

    if(loadMoreTrigger.value) {
        observer.observe(loadMoreTrigger.value);
    }
});

onUnmounted(() => {
    observer?.disconnect()
});
</script>

<template>
    <AppWrapper :selectedTab="'home'">
        <div class="feed-wrapper">
            <div class="filters">
                <Link href="/home/accepted">
                    <span :class="tab == 'accepted' ? 'selected' : ''">Accepted</span>
                </Link>

                <Link href="/home/not_decided">
                    <span :class="tab == 'not_decided' ? 'selected' : ''">Not decided</span>
                </Link>

                <Link href="/home/not_accepted">
                    <span :class="tab == 'not_accepted' ? 'selected' : ''">Not accepted</span>
                </Link>
            </div>

            <div class="feed">
                <div class="heading">
                    <h1>Home screen</h1>

                    <div class="stats">
                        <span>1046 undiscovered</span>
                        <span>91278 total</span>
                    </div>
                </div>
        
                <div class="post-wrapper" v-for="(post, index) in posts.filter(post => post.over_18 ? nsfwMode != NsfwMode.HIDE : true)" :key="post.title">
                    <a :href="'https://reddit.com' + post.permalink" target="_blank" class="post">
                        <h5>
                            <a href="https://reddit.com/r/AnimeART" target="_blank">r/AnimeART</a> 
                            by <a :href="'https://reddit.com/u/' + post.author" target="_blank">u/{{ post.author }}</a> 
                            {{ formatDistance(new Date(post.created * 1000), new Date(), { addSuffix: true }) }}
                        </h5>

                        <h1>{{ post.title }}</h1>

                        <div class="tags">
                            <span v-if="post.over_18" class="nsfw">NSFW</span>
                        </div>

                        <p>{{ post.selftext }}</p>

                        <div class="gallery" v-for="image in post.preview.images" :key="image.source.url" v-if="post.preview != null">
                            <img class="gallery-item" :src="image.source.url.replaceAll('amp;', '')" />

                            <a class="gallery-blur" v-if="post.over_18 && nsfwMode == NsfwMode.BLUR" href="javascript:void">
                                <h1>NSFW Content</h1>
                                <p>This page may contain sensitive or adult content that’s not for everyone. To view it, please log in to confirm your age.</p>
                                
                                <div class="gallery-blur-actions">
                                    <button @click="nsfwMode = NsfwMode.HIDE">I'm younger than 18</button>
                                    <button @click="nsfwMode = NsfwMode.UNBLUR">I'm over 18</button>
                                </div>
                            </a>
                        </div>

                        <div class="actions">
                            <a class="action" @click="" href="target:blank">
                                <img src="/ic_like_outlined.svg" />
                            </a>

                            <span>{{ post.score }}</span>

                            <a class="action" @click="" href="target:blank">
                                <img src="/ic_dislike_outlined.svg" />
                            </a>

                            <span class="actions-spacing"></span>

                            <div class="action">
                                <img src="/ic_comment_outlined.svg" />
                                <span>{{ post.num_comments }}</span>
                            </div>

                            <a class="action" @click="" href="target:blank">
                                <img src="/ic_favorite_outlined.svg" />
                            </a>

                            <a class="action" @click="" href="target:blank">
                                <img src="/ic_more.svg" />
                            </a>
                        </div>
                    </a>
                </div>

                <div ref="loadMoreTrigger" class="loading-trigger">
                    <span v-if="isLoading">Loading more...</span>
                </div>
            </div>
        </div>
    </AppWrapper>
</template>

<style scoped lang="scss">
    .feed-wrapper {
        flex-grow: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .loading-trigger {
        font-family: "Google Sans Flex", sans-serif;
    }

    .filters {
        padding-top: 1rem;
        padding-inline: 2rem;
        display: flex;
        gap: 1rem;

        span {
            padding-block: .4rem;
            padding-inline: 1.2rem;
            background-color: rgb(47, 2, 71);
            font-family: "Google Sans Flex", sans-serif;
            font-size: .9em;
            border-radius: .4rem;
            transition: .1s;

            &.selected {
                color: black;
                background-color: white;
            }

            &:not(.selected) {
                cursor: pointer;

                &:hover {
                    opacity: .8;
                }
            }
        }
    }

    .heading {
        margin-block: 1rem;
        display: flex;
        flex-direction: column;
        gap: .1rem;

        h1 {
            font-size: 2em;
            font-family: "Google Sans Flex", sans-serif;
            font-weight: 700;
        }
        
        .stats {
            display: flex;
            justify-content: space-between;
            font-family: "Google Sans Flex", sans-serif;
        }
    }

    .feed {
        display: flex;
        flex-direction: column;
        gap: 1.6rem;
        padding-block: 1rem;
        padding-inline: 2rem;
        width: 100%;
        max-width: 40rem;

        .post {
            h1 {
                font-size: 1.1em;
                font-weight: 600;
                font-family: "Google Sans Flex", sans-serif;
                margin-top: .2rem;
                margin-bottom: .2rem;
            }

            h5 {
                font-size: .9em;
                color: rgb(207, 172, 210);
                font-family: "Google Sans Flex", sans-serif;

                a:hover {
                    text-decoration: underline;
                }
            }

            > p {
                font-family: "Google Sans Flex", sans-serif;
                max-lines: 3;
                display: -webkit-box;
                -webkit-box-orient: vertical;
                -webkit-line-clamp: 3;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .tags {
                display: flex;
                flex-wrap: wrap;

                &:not(:empty) {
                    margin-top: .5rem;
                }

                span {
                    padding-block: .2rem;
                    padding-inline: .4rem;
                    font-size: .8em;
                    font-family: "Google Sans Flex", sans-serif;
                    border-radius: .3rem;
                }

                .nsfw {
                    background-color: rgb(229, 0, 0);
                    color: rgb(255, 255, 255);
                }
            }

            .gallery {
                width: 100%;
                display: flex;
                flex-wrap: wrap;
                justify-content: center;
                margin-top: .8rem;
                position: relative;
                background-color: black;
                border-radius: 1rem;

                .gallery-item {
                    max-height: 40rem;
                    border-radius: 1rem;
                }

                .gallery-blur {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    padding-inline: 2rem;
                    backdrop-filter: blur(1.5rem);
                    cursor: default;

                    &::before {
                        content: "";
                        position: absolute;
                        display: block;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        background-color: rgba(0, 0, 0, .75);
                        border-radius: 1rem;
                        z-index: -1;
                    }

                    h1 {
                        font-family: "Google Sans Flex", sans-serif;
                        font-size: 1.6em;
                    }

                    p {
                        font-family: "Google Sans Flex", sans-serif;
                        text-align: center;
                    }

                    .gallery-blur-actions {
                        margin-top: 1rem;
                        display: flex;
                        gap: 1rem;

                        button {
                            font-size: .95em;
                            padding-block: .4rem;
                            padding-inline: 1rem;
                            border-radius: .4rem;
                            color: black;
                            background-color: white;
                            font-family: "Google Sans Flex", sans-serif;
                            transition: .1s;
                            cursor: pointer;

                            &:hover {
                                scale: .95;
                            }
                        }
                    }
                }
            }

            .actions {
                margin-top: 1.6rem;
                margin-bottom: 1rem;
                display: flex;
                align-items: center;
                gap: 1rem;

                > span {
                    font-family: "Google Sans Flex", sans-serif;
                }

                .actions-spacing {
                    flex-grow: 1;
                }

                .action {
                    display: flex;
                    align-items: center;
                    cursor: pointer;
                    gap: .6rem;
                    transition: .1s, scale .05s;
                    position: relative;
                    z-index: 1;

                    img {
                        height: 1.6rem;
                        transition: .1s;
                    }

                    span {
                        font-family: "Google Sans Flex", sans-serif;
                        transition: .1s;
                    }

                    &::before {
                        content: "";
                        display: block;
                        position: absolute;
                        background-color: rgb(221, 135, 255);
                        width: calc(100% + 1.6rem);
                        height: calc(100% + 1.2rem);
                        top: -.6rem;
                        left: -.8rem;
                        z-index: -1;
                        opacity: 0;
                        border-radius: 2rem;
                        transition: .1s;
                    }

                    &:hover {
                        &::before {
                            opacity: 1;
                        }

                        img {
                            filter: brightness(0) saturate(100%)
                        }

                        span {
                            color: black;
                        }
                    }

                    &:active {
                        scale: .95;
                    }
                }
            }
        }
    }
</style>