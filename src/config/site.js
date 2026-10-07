import branding from '../../config/branding.json'

export const brand = Object.freeze(branding)
export const repositoryLabel = brand.repositoryUrl.replace(/^https?:\/\//, '')
