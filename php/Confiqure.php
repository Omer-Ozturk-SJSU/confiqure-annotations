<?php

declare(strict_types=1);

namespace Confiqure;

use Attribute;

/**
 * Marks a class as a confiqure.ai configuration target.
 *
 * Usage:
 *
 *     use Confiqure\Confiqure;
 *
 *     #[Confiqure(
 *         end:      '/notifications',
 *         type:     'single',
 *         tools:    ['Amazon_Connect', 'Ebay_Connect'],
 *         callback: 'https://myapp.com/webhooks/confiqure'
 *     )]
 *     final class Notifications { /* ... */ }
 *
 * The confiqure CLI scans source files for `#[Confiqure(...)]`. The backend
 * AI parses the annotated class and generates the chat playbook.
 *
 * Requires PHP 8.0+.
 */
#[Attribute(Attribute::TARGET_CLASS)]
final class Confiqure
{
    /**
     * @param string   $end      Chat endpoint segment. Defaults to snake_case class name when empty.
     * @param string   $type     Configuration shape. Defaults to "single".
     * @param string[] $tools    Names of workspace tools referenced during chat.
     * @param string   $callback End-of-chat callback URL. Falls back to workspace default when empty.
     */
    public function __construct(
        public string $end = '',
        public string $type = 'single',
        public array $tools = [],
        public string $callback = ''
    ) {
    }
}
