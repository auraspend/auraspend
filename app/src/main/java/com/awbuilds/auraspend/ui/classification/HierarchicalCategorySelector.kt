package com.awbuilds.auraspend.ui.classification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.hierarchicalCategoryMap
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing

/**
 * Hierarchical category selector dialog showing parent and subcategories.
 *
 * Aurora pass: the dialog container is an [AuraCard] with the 28dp dialog radius,
 * spacing comes from [AuraSpacing], and the confirm action is a full-width 52dp
 * rounded CTA. Selection state and callbacks are unchanged.
 */
@Composable
fun HierarchicalCategoryDialog(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    merchantSuggestion: Pair<String, Float>? = null
) {
    var selectedParent by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuraCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            style = AuraCardStyle.Filled,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(AuraSpacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.lg)
            ) {
                // Header
                Text(
                    stringResource(R.string.category_selector_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Show merchant suggestion if available
                if (merchantSuggestion != null) {
                    SuggestionChip(
                        onClick = { /* Info only */ },
                        label = {
                            Text(
                                stringResource(
                                    R.string.category_selector_ai_suggestion,
                                    merchantSuggestion.first,
                                    (merchantSuggestion.second * 100).toInt()
                                )
                            )
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Category list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                ) {
                    items(hierarchicalCategoryMap.keys.toList()) { parentCategory ->
                        val isExpanded = selectedParent == parentCategory

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParent = if (isExpanded) null else parentCategory }
                                .padding(AuraSpacing.sm)
                        ) {
                            // Parent category
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    taxonomyLabel(parentCategory),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(if (isExpanded) 90f else 0f)
                                )
                            }

                            // Subcategories
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = AuraSpacing.lg, top = AuraSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                                ) {
                                    hierarchicalCategoryMap[parentCategory]?.forEach { subcategory ->
                                        val localCategoryId = mapHierarchicalToLocalId(parentCategory)
                                        val isSelected = localCategoryId == selectedCategoryId

                                        Text(
                                            taxonomyLabel(subcategory),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier
                                                .clickable {
                                                    onCategorySelected(localCategoryId)
                                                    onDismiss()
                                                }
                                                .padding(AuraSpacing.sm)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                    }
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.action_done), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Local category id each taxonomy branch files into.
 *
 * The ids are **frozen**: they are what existing transactions store, so renaming
 * one re-files history. `hierarchicalCategoryMap` is keyed by display name and
 * carries no id of its own, so the canonical English name is the only join key
 * available — and it is matched exactly, not with `contains`. The old
 * `contains("Food")` chain answered a renamed branch with `cat_other` and no
 * trace; a miss now logs, and `TaxonomyMappingGuardTest` fails the build before a
 * rename can ship.
 */
private val branchCategoryIds: Map<String, String> = mapOf(
    "Food & Dining" to "cat_food",
    "Transport" to "cat_transport",
    "Shopping" to "cat_shopping",
    "Bills & Utilities" to "cat_bills",
    "Entertainment" to "cat_entertainment",
    "Healthcare" to "cat_healthcare",
    "Education" to "cat_education",
    "Subscriptions" to "cat_subscription",
    "Travel & Accommodation" to "cat_transport",
    "Personal Care" to "cat_personal_care",
    "Transfers" to "cat_transfer",
    "Salary & Income" to "cat_income"
)

/** Where a taxonomy branch this build does not recognise lands. Not a branch itself. */
internal const val UNMAPPED_CATEGORY_ID = "cat_other"

/** Log tag for a taxonomy miss: a rename must be visible in logcat, never silent. */
private const val TAXONOMY_TAG = "CategoryTaxonomy"

/**
 * Local category id for a top-level taxonomy branch.
 *
 * An unknown name degrades to [UNMAPPED_CATEGORY_ID] and is logged — never a
 * crash, and never a silent guess at a different branch.
 */
internal fun mapHierarchicalToLocalId(parentCategory: String): String =
    branchCategoryIds[parentCategory.trim()] ?: UNMAPPED_CATEGORY_ID.also {
        AuraLog.w(TAXONOMY_TAG, "Unmapped taxonomy branch \"$parentCategory\" — filing under $it")
    }

/**
 * Localized label for a canonical taxonomy name (a branch or a leaf), or null when
 * the name is not in the table. Display-only: the ids in [branchCategoryIds] are
 * the contract, these strings are free to translate.
 */
internal fun taxonomyLabelRes(canonicalName: String): Int? = taxonomyLabels[canonicalName.trim()]

/** Localized taxonomy label; an unknown name falls back to the canonical string. */
@Composable
private fun taxonomyLabel(canonicalName: String): String =
    taxonomyLabelRes(canonicalName)?.let { stringResource(it) } ?: canonicalName

/**
 * Display labels for every name `hierarchicalCategoryMap` currently contains.
 *
 * `TaxonomyMappingGuardTest` fails the build when the taxonomy grows a branch or a
 * leaf that is missing here, so this table cannot silently fall behind it.
 */
private val taxonomyLabels: Map<String, Int> = mapOf(
    "Food & Dining" to R.string.category_group_food_dining,
    "Food Delivery" to R.string.category_leaf_food_delivery,
    "Restaurants" to R.string.category_leaf_restaurants,
    "Groceries" to R.string.category_leaf_groceries,
    "Cafes & Bakery" to R.string.category_leaf_cafes_bakery,
    "Fast Food" to R.string.category_leaf_fast_food,
    "Meal Subscriptions" to R.string.category_leaf_meal_subscriptions,
    "Transport" to R.string.category_group_transport,
    "Auto Fuel" to R.string.category_leaf_auto_fuel,
    "Cab Services" to R.string.category_leaf_cab_services,
    "Public Transit" to R.string.category_leaf_public_transit,
    "Auto Rickshaw" to R.string.category_leaf_auto_rickshaw,
    "Parking" to R.string.category_leaf_parking,
    "Tolls" to R.string.category_leaf_tolls,
    "Flights" to R.string.category_leaf_flights,
    "Trains" to R.string.category_leaf_trains,
    "Buses" to R.string.category_leaf_buses,
    "Shopping" to R.string.category_group_shopping,
    "E-commerce" to R.string.category_leaf_e_commerce,
    "Fashion Retail" to R.string.category_leaf_fashion_retail,
    "Beauty & Cosmetics" to R.string.category_leaf_beauty_cosmetics,
    "Electronics" to R.string.category_leaf_electronics,
    "Home & Furniture" to R.string.category_leaf_home_furniture,
    "Sports & Outdoors" to R.string.category_leaf_sports_outdoors,
    "Jewelry" to R.string.category_leaf_jewelry,
    "Books" to R.string.category_leaf_books,
    "Bills & Utilities" to R.string.category_group_bills_utilities,
    "Electricity Bill" to R.string.category_leaf_electricity_bill,
    "Water Bill" to R.string.category_leaf_water_bill,
    "Gas Bill" to R.string.category_leaf_gas_bill,
    "Internet Bill" to R.string.category_leaf_internet_bill,
    "Mobile Recharge" to R.string.category_leaf_mobile_recharge,
    "DTH/TV" to R.string.category_leaf_dth_tv,
    "Rent" to R.string.category_leaf_rent,
    "Entertainment" to R.string.category_group_entertainment,
    "Streaming Video" to R.string.category_leaf_streaming_video,
    "Streaming Audio/Music" to R.string.category_leaf_streaming_audio_music,
    "Gaming" to R.string.category_leaf_gaming,
    "Movies & Events" to R.string.category_leaf_movies_events,
    "Sports" to R.string.category_leaf_sports,
    "Healthcare" to R.string.category_group_healthcare,
    "Medical Consultations" to R.string.category_leaf_medical_consultations,
    "Pharmacy" to R.string.category_leaf_pharmacy,
    "Gym & Fitness" to R.string.category_leaf_gym_fitness,
    "Dental" to R.string.category_leaf_dental,
    "Diagnostics" to R.string.category_leaf_diagnostics,
    "Mental Health" to R.string.category_leaf_mental_health,
    "Education" to R.string.category_group_education,
    "Online Courses" to R.string.category_leaf_online_courses,
    "Tuitions" to R.string.category_leaf_tuitions,
    "Books & Materials" to R.string.category_leaf_books_materials,
    "College/School Fees" to R.string.category_leaf_college_school_fees,
    "Professional Certifications" to R.string.category_leaf_professional_certifications,
    "Subscriptions" to R.string.category_group_subscriptions,
    "SaaS & Software" to R.string.category_leaf_saas_software,
    "Cloud Services" to R.string.category_leaf_cloud_services,
    "Memberships" to R.string.category_leaf_memberships,
    "Premium Services" to R.string.category_leaf_premium_services,
    "Travel & Accommodation" to R.string.category_group_travel_accommodation,
    "Hotels & Stays" to R.string.category_leaf_hotels_stays,
    "Flight Bookings" to R.string.category_leaf_flight_bookings,
    "Train Tickets" to R.string.category_leaf_train_tickets,
    "Bus Tickets" to R.string.category_leaf_bus_tickets,
    "Tour Packages" to R.string.category_leaf_tour_packages,
    "Personal Care" to R.string.category_group_personal_care,
    "Haircut & Salon" to R.string.category_leaf_haircut_salon,
    "Skincare" to R.string.category_leaf_skincare,
    "Personal Grooming" to R.string.category_leaf_personal_grooming,
    "Transfers" to R.string.category_group_transfers,
    "Bank Transfers" to R.string.category_leaf_bank_transfers,
    "UPI Transfers" to R.string.category_leaf_upi_transfers,
    "Peer-to-Peer" to R.string.category_leaf_peer_to_peer,
    "Loan Repayment" to R.string.category_leaf_loan_repayment,
    "Salary & Income" to R.string.category_group_salary_income,
    "Monthly Salary" to R.string.category_leaf_monthly_salary,
    "Bonus" to R.string.category_leaf_bonus,
    "Freelance Income" to R.string.category_leaf_freelance_income,
    "Investment Returns" to R.string.category_leaf_investment_returns
)
