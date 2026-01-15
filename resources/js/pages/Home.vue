<script setup lang="ts">
import AppWrapper from '@/layouts/AppWrapper.vue';
import { Link } from '@inertiajs/vue3';
import { Ref, ref } from 'vue';
import { formatDistance, subDays } from "date-fns";

interface Props {
    tab: "accepted" | "not_decided" | "not_accepted"
}

withDefaults(defineProps<Props>(), {
    tab: "accepted"
});

const posts: Ref<RedditPost[]> = ref([]);

interface RedditPost {
    author: string,
    title: string,
    selftext: string,
    score: number,
    created: number,
    permalink: string,
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
    const response = await (await fetch("https://www.reddit.com/r/AnimeART/top.json?limit=100&t=all")).json();
    for(const post of response.data.children) {
        posts.value.push(post.data);
    }
}

fetchRedditPosts();
</script>

<template>
    <Head title="Home"></Head>

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

                <div class="post-wrapper" v-for="post in posts" :key="post.title">
                    <a :href="'https://reddit.com' + post.permalink" target="_blank" class="post">
                        <h5>
                            <a href="https://reddit.com/r/AnimeART" target="_blank">r/AnimeART</a> 
                            by <a :href="'https://reddit.com/u/' + post.author" target="_blank">u/{{ post.author }}</a> 
                            {{ formatDistance(new Date(post.created * 1000), new Date(), { addSuffix: true }) }}
                        </h5>

                        <h1>{{ post.title }}</h1>
                        <p>{{ post.selftext }}</p>

                        <div class="gallery" v-for="image in post.preview.images" :key="image.source.url" v-if="post.preview != null">
                            <img class="gallery-item" :src="image.source.url.replaceAll('amp;', '')" />
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
        gap: 1rem;
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

            p {
                font-family: "Google Sans Flex", sans-serif;
                max-lines: 3;
                display: -webkit-box;
                -webkit-box-orient: vertical;
                -webkit-line-clamp: 3;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .gallery {
                width: 100%;
                display: flex;
                flex-wrap: wrap;
                justify-content: center;
                margin-top: .4rem;
                margin-bottom: 1rem;

                .gallery-item {
                    max-height: 40rem;
                    border-radius: 1rem;
                }
            }

            .actions {
                margin-top: .8rem;
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